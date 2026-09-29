/* ============================================================
   ET虚拟机 · 浏览器测试桥接（仅在 ?test=1 且无原生桥时启用）
   v2.0.0 · ET协会出品 · © ET
   ============================================================ */
(function () {
  if (window.ETBridge || location.search.indexOf('test=1') < 0) return;

  var href = location.href.split('#')[0];
  var base = href.slice(0, href.lastIndexOf('/') + 1);       // .../assets/
  var samples = base + '../test/samples/';                   // 不带 file:// 前缀
  window.__samples = samples;

  var state = location.hash.slice(1) || 'welcome';
  var dlState = {};

  /* 预置虚拟机：必须在 app.js 读取 getVmData 之前写入 localStorage */
  (function seed() {
    var needVm = ['settings', 'boot', 'bootfull', 'bootquick', 'vm', 'store', 'files', 'files-shared', 'compat64', 'compat32', 'compatjava'].indexOf(state) > -1;
    if (!needVm) return;
    var installed = (state.indexOf('compat') === 0)
      ? []
      : [{ label: 'ET 浏览器', pkg: 'com.et.browser', abi: '兼容', color: 'tile-blue' }];
    var vm = {
      id: 1, name: '我的安卓机', bits: '64', ver: 'Android 13.0', model: '通用机型',
      rom: { name: 'ET-Rom-Android13-arm64.zip', size: 1420000000, bits: '64' },
      version: 'ET-OS 14.0 (2026.09)', fps: 120, anim: '极光粒子', animPath: '',
      gapps: true, xposed: true, root: true,
      firstBoot: (state === 'boot' || state === 'bootfull') ? true : false,
      installed: installed, created: Date.now()
    };
    localStorage.setItem('vms', JSON.stringify([vm]));
  })();

  window.ETBridge = {
    getDeviceInfo: function () {
      return JSON.stringify({ abi: 'arm64-v8a', abiList: ['arm64-v8a'], model: 'ET-TestPhone', brand: 'ET', sdk: 34, android: '14' });
    },
    getVmData: function () { return localStorage.getItem('vms') || '[]'; },
    saveVmData: function (j) { localStorage.setItem('vms', j); },
    toast: function (m) { console.log('[toast]', m); },
    vibrate: function () {},
    requestPermission: function (kind) {
      if (window.ETVM && window.ETVM.onPermissionResult) window.ETVM.onPermissionResult(kind, true);
    },
    hasPermission: function (kind) { return true; },
    pickFile: function () {},
    getInstalledApps: function () {
      return JSON.stringify([
        { label: '示例浏览器', pkg: 'com.example.browser', apk: '', size: 12345678, system: false },
        { label: '示例相机', pkg: 'com.example.camera', apk: '', size: 23456789, system: true },
        { label: '纯Java工具', pkg: 'com.example.pure', apk: '', size: 1111111, system: false }
      ]);
    },
    importApk: function (path, vmId) {
      var f = 'appmixed.apk';
      if (window.__impMode === '64') f = 'app64.apk';
      if (window.__impMode === '32') f = 'app32.apk';
      if (window.__impMode === 'java') f = 'appjava.apk';
      return samples + f;
    },
    startFloat: function () {},
    stopFloat: function () {},
    /* ---- v2.0.0 新增：ROM 商店 ---- */
    downloadRom: function (url, name) {
      dlState['dl1'] = { step: 0 };
      return '1';
    },
    getDownloadInfo: function (id) {
      var s = dlState['dl1'];
      if (!s) return '{}';
      if (s.step === 0) { s.step = 1; return JSON.stringify({ bytes: 137, total: 343, done: false, failed: false, pending: true }); }
      return JSON.stringify({ bytes: 343, total: 343, done: true, failed: false, pending: false, path: samples + 'android-rom-arm64.zip' });
    },
    /* ---- v2.0.0 新增：连接储存 / 共享文件夹 ---- */
    getSharedDir: function () { return '/data/user/0/com.et.vm/files/shared'; },
    getVmDir: function (vmId) { return '/data/user/0/com.et.vm/files/vms/' + vmId + '/files'; },
    listVmFiles: function (vmId) {
      return JSON.stringify([
        { name: 'hello.txt', size: 34, dir: false, path: samples + 'hello.txt' },
        { name: 'picture.png', size: 9999, dir: false, path: samples + 'picture.png' }
      ]);
    },
    listSharedFiles: function () {
      return JSON.stringify([
        { name: 'shared-note.txt', size: 21, dir: false, path: samples + 'shared-note.txt' },
        { name: 'photo.jpg', size: 4096, dir: false, path: samples + 'photo.jpg' }
      ]);
    },
    readFileText: function (path, limitKb) {
      return path.indexOf('hello') > -1 ? '你好，ET 虚拟机。\n来自真机的文件内容。' : (path.indexOf('shared') > -1 ? '这是真机共享的文件' : 'BINARY');
    },
    readFileBase64: function (path, maxBytes) {
      return path.indexOf('picture') > -1 ? 'iVBORw0KGgo=' : '';
    },
    copyFromShared: function (vmId, name) { return true; },
    deleteVmFile: function (vmId, name) { return true; }
  };

  var log = document.createElement('div');
  log.id = 'testlog';
  log.style.cssText = 'position:fixed;bottom:0;left:0;right:0;background:#000;color:#0f0;font:10px monospace;padding:2px;z-index:999;';
  function L(m) { log.innerHTML += '<div>' + m + '</div>'; }
  document.body.appendChild(log);
  L('state=' + state);

  function romUrl(f) { return samples + f; }

  function diag() { return document.getElementById('testlog').textContent; }

  function drive() {
    try {
      switch (state) {
        case 'welcome': break;
        case 'home': window.ETVM.goHome(); break;
        case 'wizard': window.ETVM.openWizard(); break;
        case 'detect64':
          window.ETVM.openWizard(); window.ETVM.wizardNext();
          window.ETVM.onFileReady('rom', romUrl('android-rom-arm64pure.zip'), 'ET-Rom-Android13-arm64.zip', 1420000000, '');
          break;
        case 'detectwin':
          window.ETVM.openWizard(); window.ETVM.wizardNext();
          window.ETVM.onFileReady('rom', romUrl('windows11.iso'), 'Windows11_Pro.iso', 4800000000, '');
          break;
        case 'detect32':
          window.ETVM.openWizard(); window.ETVM.wizardNext();
          window.ETVM.onFileReady('rom', romUrl('android-rom-arm32.zip'), 'LineageOS18_arm32.zip', 780000000, '');
          break;
        case 'detect32fix':
          window.ETVM.openWizard(); window.ETVM.wizardNext();
          window.ETVM.onFileReady('rom', romUrl('android-rom-arm32.zip'), 'LineageOS18_arm32.zip', 780000000, '');
          setTimeout(function () { window.ETVM.forceBits('32'); }, 1500);
          break;
        case 'perm':
          window.ETVM.openWizard(); window.ETVM.wizardNext();
          window.ETVM.onFileReady('rom', romUrl('android-rom-arm64.zip'), 'ET-Rom-Android13-arm64.zip', 1420000000, '');
          setTimeout(function () {
            window.ETVM.wizardNext();
            window.ETVM.onPermissionResult('storage', true);
            window.ETVM.onPermissionResult('overlay', true);
            window.ETVM.onPermissionResult('install', true);
            window.ETVM.onPermissionResult('notify', true);
          }, 1500);
          break;
        case 'summary':
          window.ETVM.openWizard(); window.ETVM.wizardNext();
          window.ETVM.onFileReady('rom', romUrl('android-rom-arm64.zip'), 'ET-Rom-Android13-arm64.zip', 1420000000, '');
          setTimeout(function () {
            window.ETVM.wizardNext();
            window.ETVM.onPermissionResult('storage', true);
            window.ETVM.onPermissionResult('overlay', true);
            window.ETVM.onPermissionResult('install', true);
            window.ETVM.wizardNext();
          }, 1500);
          break;
        case 'settings':
          window.ETVM.goHome(); window.ETVM.openSettings(1);
          setTimeout(function () {
            L('DIAG gapps=' + document.getElementById('optGapps').checked +
              ' xposed=' + document.getElementById('optXposed').checked +
              ' root=' + document.getElementById('optRoot').checked +
              ' fps=' + document.getElementById('fpsRange').value);
          }, 300);
          break;
        case 'boot':
          window.ETVM.startVm(1);
          setTimeout(function () {
            var lines = document.getElementById('bootLines');
            L('DIAG midboot=' + (lines && lines.textContent.indexOf('首次启动') > -1) +
              ' romline=' + (lines && lines.textContent.indexOf('安装 ROM') > -1));
          }, 3000);
          break;
        case 'bootfull':
          window.ETVM.startVm(1);
          break;
        case 'bootquick':
          window.ETVM.startVm(1);
          setTimeout(function () {
            var lines = document.getElementById('bootLines');
            L('DIAG quick=' + (lines && lines.textContent.indexOf('快速启动') > -1));
          }, 3200);
          break;
        case 'vm': window.ETVM.startVm(1); break;
        case 'store': window.ETVM.openSettings(1); window.ETVM.openStore(); break;
        case 'about': window.ETVM.goHome(); document.querySelector('[data-go="about"]').click(); break;
        case 'romstore':
          window.ETVM.openRomStore();
          setTimeout(function () {
            var cards = document.querySelectorAll('.rom-card').length;
            var st = document.getElementById('romstatus-x86-44-r1');
            L('DIAG cards=' + cards + ' status1=' + (st ? st.textContent : 'none'));
          }, 800);
          break;
        case 'romstore-download':
          window.ETVM.openRomStore();
          setTimeout(function () { window.ETVM.downloadRom('x86-44-r1'); }, 600);
          setTimeout(function () {
            var st = document.getElementById('romstatus-x86-44-r1');
            var use = document.getElementById('romuse-x86-44-r1');
            L('DIAG status1=' + (st ? st.textContent : 'none') + ' usebtn=' + (use ? 'yes' : 'no'));
          }, 3200);
          break;
        case 'files':
          window.ETVM.openSettings(1); window.ETVM.openFiles();
          setTimeout(function () {
            var rows = document.querySelectorAll('.file-row').length;
            var hint = document.getElementById('sharedPathHint');
            L('DIAG rows=' + rows + ' hint=' + (hint ? hint.textContent.length : 0));
          }, 500);
          break;
        case 'files-shared':
          window.ETVM.openSettings(1); window.ETVM.openFiles();
          setTimeout(function () {
            document.querySelector('#filesTabSeg .seg-btn[data-tab="shared"]').click();
          }, 300);
          setTimeout(function () {
            var rows = document.querySelectorAll('.file-row').length;
            var btns = document.querySelectorAll('.file-actions .btn').length;
            L('DIAG rows=' + rows + ' actions=' + btns);
          }, 700);
          break;
        case 'compat64':
          window.__impMode = '64'; window.ETVM.openSettings(1); window.ETVM.openStore();
          window.ETVM.importFromDevice(); window.ETVM.pickApp('com.example.browser');
          break;
        case 'compat32':
          window.__impMode = '32'; window.ETVM.openSettings(1); window.ETVM.openStore();
          window.ETVM.importFromDevice(); window.ETVM.pickApp('com.example.browser');
          break;
        case 'compatjava':
          window.__impMode = 'java'; window.ETVM.openSettings(1); window.ETVM.openStore();
          window.ETVM.importFromDevice(); window.ETVM.pickApp('com.example.pure');
          break;
      }
    } catch (e) { L('ERR:' + e.message); }
    L('drive done');
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', drive);
  else drive();
})();
