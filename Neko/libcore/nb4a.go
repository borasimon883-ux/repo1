package libcore

import (
	"context"
	"encoding/json"
	"fmt"
	"libcore/device"
	"net"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"time"
	_ "unsafe"

	"log"

	"github.com/matsuridayo/libneko/neko_common"
	"github.com/matsuridayo/libneko/neko_log"
	"golang.org/x/sys/unix"
)

//go:linkname resourcePaths github.com/sagernet/sing-box/constant.resourcePaths
var resourcePaths []string

func NekoLogPrintln(s string) {
	log.Println(s)
}

func NekoLogClear() {
	neko_log.LogWriter.Truncate()
}

func ForceGc() {
	go runtime.GC()
}

func InitCore(process, cachePath, internalAssets, externalAssets string,
	maxLogSizeKb int32, logEnable bool,
	if1 NB4AInterface, if2 BoxPlatformInterface,
) {
	defer device.DeferPanicToError("InitCore", func(err error) { log.Println(err) })
	isBgProcess = strings.HasSuffix(process, ":bg")

	neko_common.RunMode = neko_common.RunMode_NekoBoxForAndroid
	intfNB4A = if1
	intfBox = if2
	useProcfs = intfBox.UseProcFS()

	// Working dir
	tmp := filepath.Join(cachePath, "../no_backup")
	os.MkdirAll(tmp, 0755)
	os.Chdir(tmp)

	// sing-box fs
	resourcePaths = append(resourcePaths, externalAssets)

	// Set up log
	if maxLogSizeKb < 50 {
		maxLogSizeKb = 50
	}
	neko_log.LogWriterDisable = !logEnable
	neko_log.TruncateOnStart = isBgProcess
	neko_log.SetupLog(int(maxLogSizeKb)*1024, filepath.Join(cachePath, "neko.log"))

	// Set up some component
	go func() {
		defer device.DeferPanicToError("InitCore-go", func(err error) { log.Println(err) })
		device.GoDebug(process)

		externalAssetsPath = externalAssets
		internalAssetsPath = internalAssets

		// certs
		pem, err := os.ReadFile(externalAssetsPath + "ca.pem")
		if err == nil {
			updateRootCACerts(pem)
		}

		// bg
		if isBgProcess {
			extractAssets()
		}
	}()
}

func sendFdToProtect(fd int, path string) error {
	socketFd, err := unix.Socket(unix.AF_UNIX, unix.SOCK_STREAM, 0)
	if err != nil {
		return fmt.Errorf("failed to create unix socket: %w", err)
	}
	defer unix.Close(socketFd)

	var timeout unix.Timeval
	timeout.Usec = 100 * 1000

	_ = unix.SetsockoptTimeval(socketFd, unix.SOL_SOCKET, unix.SO_RCVTIMEO, &timeout)
	_ = unix.SetsockoptTimeval(socketFd, unix.SOL_SOCKET, unix.SO_SNDTIMEO, &timeout)

	err = unix.Connect(socketFd, &unix.SockaddrUnix{Name: path})
	if err != nil {
		return fmt.Errorf("failed to connect: %w", err)
	}

	err = unix.Sendmsg(socketFd, nil, unix.UnixRights(fd), nil, 0)
	if err != nil {
		return fmt.Errorf("failed to send: %w", err)
	}

	dummy := []byte{1}
	n, err := unix.Read(socketFd, dummy)
	if err != nil {
		return fmt.Errorf("failed to receive: %w", err)
	}
	if n != 1 {
		return fmt.Errorf("socket closed unexpectedly")
	}
	return nil
}

// PingResult represents the result of a ping test for a single host
type PingResult struct {
	Host    string `json:"host"`
	PingMs  int    `json:"ping_ms"`
	Success bool   `json:"success"`
	Error   string `json:"error,omitempty"`
}

// ParallelPing performs TCP handshake ping tests on multiple hosts concurrently
// addresses: JSON array of "host:port" strings
// timeoutMs: timeout in milliseconds for each ping test
// Returns: JSON string with array of PingResult objects
func ParallelPing(addresses string, timeoutMs int32) string {
	defer device.DeferPanicToError("ParallelPing", func(err error) { 
		log.Printf("ParallelPing error: %v", err) 
	})

	var hostPorts []string
	if err := json.Unmarshal([]byte(addresses), &hostPorts); err != nil {
		log.Printf("ParallelPing: failed to parse addresses JSON: %v", err)
		return "[]"
	}

	if timeoutMs <= 0 {
		timeoutMs = 3000 // Default 3 seconds timeout
	}

	results := make([]PingResult, len(hostPorts))
	var wg sync.WaitGroup
	
	// Create context with timeout
	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(timeoutMs+1000)*time.Millisecond)
	defer cancel()

	// Perform ping tests concurrently
	for i, hostPort := range hostPorts {
		wg.Add(1)
		go func(index int, address string) {
			defer wg.Done()
			results[index] = tcpPing(ctx, address, time.Duration(timeoutMs)*time.Millisecond)
		}(i, hostPort)
	}

	// Wait for all goroutines to complete
	wg.Wait()

	// Convert results to JSON
	jsonBytes, err := json.Marshal(results)
	if err != nil {
		log.Printf("ParallelPing: failed to marshal results: %v", err)
		return "[]"
	}

	return string(jsonBytes)
}

// tcpPing performs a TCP handshake ping to a single host:port
func tcpPing(ctx context.Context, hostPort string, timeout time.Duration) PingResult {
	result := PingResult{
		Host:    hostPort,
		Success: false,
	}

	// Create a context with timeout for this specific ping
	pingCtx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	// Record start time
	start := time.Now()

	// Create dialer with timeout
	dialer := &net.Dialer{
		Timeout: timeout,
	}

	// Attempt TCP connection
	conn, err := dialer.DialContext(pingCtx, "tcp", hostPort)
	if err != nil {
		result.Error = err.Error()
		return result
	}

	// Close connection immediately after successful handshake
	conn.Close()

	// Calculate ping time
	duration := time.Since(start)
	result.PingMs = int(duration.Milliseconds())
	result.Success = true

	return result
}
