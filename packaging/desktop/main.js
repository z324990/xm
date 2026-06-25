const { app, BrowserWindow, Tray, Menu, dialog, nativeImage } = require('electron');
const path = require('path');
const { spawn } = require('child_process');
const fs = require('fs');

// ===== Configuration =====
const SERVER_PORT = 8080;
const isDev = process.argv.includes('--dev');
const isPackaged = app.isPackaged;

let mainWindow = null;
let tray = null;
let serverProcess = null;
let isQuitting = false;

// ===== Path resolution =====
function getResourcePath(...segments) {
  if (isPackaged) {
    return path.join(process.resourcesPath, ...segments);
  }
  return path.join(__dirname, ...segments);
}

function getBackendJarPath() {
  if (isDev) {
    return path.resolve(__dirname, '../../target/ai-chat-platform-1.0.0.jar');
  }
  return getResourcePath('backend', 'ai-chat-platform.jar');
}

function getDataDir() {
  if (isDev) {
    return path.resolve(__dirname, '../../data');
  }
  const userDataPath = app.getPath('userData');
  return path.join(userDataPath, 'data');
}

function getUploadsDir() {
  return path.join(getDataDir(), '../uploads');
}

// ===== Java detection =====
function findJava() {
  // Try bundled JRE first
  const bundledJRE = getResourcePath('jre', 'bin', 'java.exe');
  if (fs.existsSync(bundledJRE)) return bundledJRE;

  // Check JAVA_HOME
  const javaHome = process.env.JAVA_HOME;
  if (javaHome) {
    const javaExe = path.join(javaHome, 'bin', 'java.exe');
    if (fs.existsSync(javaExe)) return javaExe;
    const javaBin = path.join(javaHome, 'bin', 'java');
    if (fs.existsSync(javaBin)) return javaBin;
  }

  // Fall back to PATH
  return 'java';
}

// ===== Server management =====
function startServer() {
  return new Promise((resolve, reject) => {
    const jarPath = getBackendJarPath();
    const dataDir = getDataDir();
    const uploadsDir = path.join(dataDir, '../uploads');

    if (!fs.existsSync(jarPath)) {
      // In dev mode, assume server is already running
      if (isDev) {
        console.log('Dev mode: assuming server is running on port', SERVER_PORT);
        resolve();
        return;
      }
      reject(new Error(`Backend JAR not found: ${jarPath}`));
      return;
    }

    // Ensure data directories exist
    if (!fs.existsSync(dataDir)) {
      fs.mkdirSync(dataDir, { recursive: true });
    }
    if (!fs.existsSync(uploadsDir)) {
      fs.mkdirSync(uploadsDir, { recursive: true });
    }

    const javaExe = findJava();
    console.log('Using Java:', javaExe);
    console.log('Starting server from:', jarPath);
    console.log('Data directory:', dataDir);

    serverProcess = spawn(javaExe, [
      '-jar', jarPath,
      `--server.port=${SERVER_PORT}`,
      `--spring.datasource.url=jdbc:h2:file:${dataDir.replace(/\\/g, '/')}/chatdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`,
      `--app.upload.dir=${uploadsDir}`,
      '--logging.level.com.chat=INFO'
    ], {
      cwd: path.dirname(jarPath),
      stdio: ['pipe', 'pipe', 'pipe']
    });

    let started = false;
    let startupBuffer = '';

    serverProcess.stdout.on('data', (data) => {
      const text = data.toString();
      startupBuffer += text;
      console.log('[Server]', text.trim());

      if (!started && text.includes('Started ChatApplication')) {
        started = true;
        resolve();
      }
    });

    serverProcess.stderr.on('data', (data) => {
      const text = data.toString();
      startupBuffer += text;
      console.log('[Server-ERR]', text.trim());
    });

    serverProcess.on('error', (err) => {
      console.error('Failed to start server:', err);
      if (!started) {
        started = true;
        reject(err);
      }
    });

    serverProcess.on('exit', (code, signal) => {
      console.log('Server exited with code:', code, 'signal:', signal);
      if (!started) {
        started = true;
        reject(new Error(`Server exited with code ${code}`));
      }
    });

    // Timeout after 30 seconds
    setTimeout(() => {
      if (!started) {
        started = true;
        serverProcess.kill();
        reject(new Error('Server startup timed out. Check for port conflicts.\n' + startupBuffer));
      }
    }, 30000);
  });
}

function stopServer() {
  return new Promise((resolve) => {
    if (!serverProcess) {
      resolve();
      return;
    }
    serverProcess.on('exit', () => resolve());
    serverProcess.kill('SIGTERM');
    setTimeout(() => {
      if (serverProcess) {
        serverProcess.kill('SIGKILL');
      }
      resolve();
    }, 5000);
  });
}

// ===== Window creation =====
function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 800,
    minHeight: 600,
    title: 'AI Chat',
    icon: path.join(__dirname, 'assets', 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true,
      spellcheck: true
    },
    show: false,
    backgroundColor: '#f8fafc'
  });

  // Load the app
  const url = `http://localhost:${SERVER_PORT}`;
  mainWindow.loadURL(url);

  // Show when ready
  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
    if (isDev) {
      mainWindow.webContents.openDevTools();
    }
  });

  // Handle external links
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    require('electron').shell.openExternal(url);
    return { action: 'deny' };
  });

  mainWindow.on('close', (e) => {
    if (!isQuitting) {
      e.preventDefault();
      mainWindow.hide();
    }
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

// ===== Tray icon =====
function createTray() {
  // Create a simple 16x16 tray icon using nativeImage
  const iconSize = 16;
  const canvas = Buffer.alloc(iconSize * iconSize * 4);
  // Fill with primary color (#4f46e5)
  for (let i = 0; i < iconSize * iconSize; i++) {
    canvas[i * 4] = 79;     // R
    canvas[i * 4 + 1] = 70; // G
    canvas[i * 4 + 2] = 229;// B
    canvas[i * 4 + 3] = 255;// A
  }
  const icon = nativeImage.createFromBuffer(canvas, { width: iconSize, height: iconSize });

  tray = new Tray(icon);
  tray.setToolTip('AI Chat');

  const contextMenu = Menu.buildFromTemplate([
    {
      label: '打开 AI Chat',
      click: () => {
        if (mainWindow) {
          mainWindow.show();
          mainWindow.focus();
        }
      }
    },
    { type: 'separator' },
    {
      label: '退出',
      click: () => {
        isQuitting = true;
        app.quit();
      }
    }
  ]);

  tray.setContextMenu(contextMenu);
  tray.on('double-click', () => {
    if (mainWindow) {
      mainWindow.show();
      mainWindow.focus();
    }
  });
}

// ===== App lifecycle =====
app.whenReady().then(async () => {
  createTray();

  try {
    await startServer();
    console.log('Server started successfully');
  } catch (err) {
    if (isDev) {
      console.log('Dev mode: proceeding without server check');
    } else {
      dialog.showErrorBox('启动失败', `无法启动后端服务:\n${err.message}\n\n请确保 Java 17+ 已安装。`);
      app.quit();
      return;
    }
  }

  createWindow();

  app.on('activate', () => {
    if (mainWindow === null) {
      createWindow();
    } else {
      mainWindow.show();
    }
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    // On Windows/Linux, keep running in tray
  }
});

app.on('before-quit', async () => {
  isQuitting = true;
  await stopServer();
});

// Prevent multiple instances
const gotLock = app.requestSingleInstanceLock();
if (!gotLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.show();
      mainWindow.focus();
    }
  });
}
