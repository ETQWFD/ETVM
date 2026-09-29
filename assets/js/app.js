/* ============================================================
   ET虚拟机 v2.0.0 · 前端逻辑
   ET协会出品 · © ET
   ============================================================ */
(function () {
  'use strict';
  const B = window.ETBridge;
  const $ = (id) => document.getElementById(id);
  const $$ = (sel) => document.querySelectorAll(sel);

  const SCREENS = ['welcome', 'home', 'wizard', 'settings', 'boot', 'vm', 'store', 'romstore', 'files', 'developer', 'about'];
  const COLORS = ['tile-blue', 'tile-cyan', 'tile-orange', 'tile-green', 'tile-purple', 'tile-red'];

  let vms = [];
  try { vms = JSON.parse(B.getVmData() || '[]'); } catch (e) { vms = []; }

  let curVm = null;
  let curScreen = 'welcome';
  let importCache = [];
  let pendingInstall = null;
  let catalog = [];
  let romDls = {};
  let dlTimers = {};
  let filesTab = 'vm';

  const wizard = {
    step: 1,
    bits: '64',
    ver: 'Android 13.0',
    model: '通用机型',
    name: '我的安卓机',
    rom: null,
    perms: { storage: false, overlay: false, install: false, notify: false }
  };

  /* ---------------- 基础 ---------------- */
  function show(id) {
    SCREENS.forEach(s => $(`screen-${s}`).classList.toggle('active', s === id));
    curScreen = id;
  }
  function toast(msg) { B.toast(msg); }
  function vibrate(ms) { B.vibrate(ms); }
  function fmtSize(n) {
    if (!n) return '未知';
    if (n > 1073741824) return (n / 1073741824).toFixed(2) + ' GB';
    if (n > 1048576) return (n / 1048576).toFixed(1) + ' MB';
    return (n / 1024).toFixed(0) + ' KB';
  }
  function save() { B.saveVmData(JSON.stringify(vms)); }
  function findVm(id) { return vms.find(v => v.id === id); }
  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])); }
  function colorFor(pkg) {
    let h = 0;
    for (let i = 0; i < pkg.length; i++) h = (h * 31 + pkg.charCodeAt(i)) >>> 0;
    return COLORS[h % COLORS.length];
  }
  function firstChar(s) { return s ? s.trim().charAt(0) : '?'; }
  function bitsLabel(b) {
    return { '64': '64 位 (arm64 / x86_64)', '32': '32 位 (armv7 / x86)', 'both': '混合（32+64 位兼容）', 'unknown': '未知（未发现明确 ABI 标记）' }[b] || '未知';
  }

  /* ---------------- 欢迎页 ---------------- */
  function typewriter(el, text, speed, done) {
    let i = 0;
    el.textContent = '';
    const t = setInterval(() => {
      if (i <= text.length) {
        el.textContent = text.slice(0, i) + '▌';
        i++;
      } else {
        clearInterval(t);
        el.textContent = text;
        if (done) done();
      }
    }, speed || 46);
  }
  function goHome() {
    const info = JSON.parse(B.getDeviceInfo());
    const d = new Date();
    const days = ['日', '一', '二', '三', '四', '五', '六'];
    $('homeGreeting').textContent = '欢迎回来 · 周' + days[d.getDay()];
    $('homeSub').textContent = 'ET 虚拟机管理台 · ' + info.android + ' · ' + info.model;
    $('deviceAbi').textContent = info.abi;
    renderHome();
    show('home');
  }

  /* ---------------- 首页 ---------------- */
  function renderHome() {
    $('vmCount').textContent = vms.length;
    const list = $('vmList');
    if (!vms.length) {
      list.innerHTML = '<div class="empty">还没有虚拟机，点击右下角 + 创建第一台</div>';
      return;
    }
    list.innerHTML = vms.map(v => {
      const ready = v.firstBoot === false;
      return `
      <div class="vm-card">
        <div class="vm-card-head">
          <div class="vm-avatar">${esc(firstChar(v.name))}</div>
          <div>
            <b>${esc(v.name)}</b>
            <span class="sub">${esc(v.rom ? v.rom.name : '未安装 ROM')} · ${esc(v.ver)}</span>
          </div>
          <span class="badge ${ready ? 'badge-ok' : 'badge-off'}">${ready ? '已就绪' : '待首启'}</span>
        </div>
        <div class="vm-card-actions">
          <button class="btn btn-primary" onclick="ETVM.startVm(${v.id})">启动</button>
          <button class="btn" onclick="ETVM.openSettings(${v.id})">设置</button>
          <button class="btn" onclick="ETVM.removeVm(${v.id})">删除</button>
        </div>
      </div>`;
    }).join('');
  }
  function removeVm(id) {
    if (!confirm('确定删除虚拟机「' + findVm(id).name + '」吗？')) return;
    vms = vms.filter(v => v.id !== id);
    save();
    toast('已删除虚拟机');
    renderHome();
  }

  /* ---------------- 向导 ---------------- */
  function openWizard() {
    wizard.step = 1;
    wizard.rom = null;
    wizard.perms = { storage: false, overlay: false, install: false, notify: false };
    renderWizard();
    show('wizard');
  }
  function renderWizard() {
    $('wizardStepLabel').textContent = '第 ' + wizard.step + ' 步 / 4 · ' + ['系统环境', '上传 ROM 检测', '授权权限', '创建确认'][wizard.step - 1];
    $$('#wizardSteps .st').forEach((el, i) => el.classList.toggle('on', i < wizard.step));
    for (let i = 1; i <= 4; i++) $('wstep' + i).classList.toggle('hidden', i !== wizard.step);
    if (wizard.step === 4) renderSummary();
  }
  function wizardNext() {
    if (wizard.step === 1) {
      wizard.bits = $$('#bitsSeg .seg-btn.on')[0].dataset.bits;
      wizard.ver = $$('#verSeg .seg-btn.on')[0].dataset.ver;
      if (wizard.ver === 'custom') wizard.ver = $('vmCustomVer').value.trim() || '自定义版本';
      wizard.model = $$('#modelSeg .seg-btn.on')[0].dataset.model;
      wizard.name = $('vmName').value.trim() || '我的安卓机';
      wizard.step = 2;
    } else if (wizard.step === 2) {
      if (!wizard.rom || !wizard.rom.ok) return;
      wizard.step = 3;
      refreshPerms();
    } else if (wizard.step === 3) {
      if (!(wizard.perms.storage && wizard.perms.overlay && wizard.perms.install)) {
        toast('请先授权存储、悬浮窗与安装权限');
        return;
      }
      wizard.step = 4;
    } else {
      return;
    }
    renderWizard();
  }
  function wizardBack() {
    if (wizard.step === 1) { backHome(); return; }
    wizard.step--;
    renderWizard();
  }

  $('bitsSeg').addEventListener('click', e => {
    const b = e.target.closest('.seg-btn');
    if (!b) return;
    $$('#bitsSeg .seg-btn').forEach(x => x.classList.toggle('on', x === b));
    wizard.bits = b.dataset.bits;
    if (wizard.rom && wizard.rom.det) refreshRomMatch();
  });
  $('verSeg').addEventListener('click', e => {
    const b = e.target.closest('.seg-btn');
    if (!b) return;
    $$('#verSeg .seg-btn').forEach(x => x.classList.toggle('on', x === b));
    $('vmCustomVer').classList.toggle('hidden', b.dataset.ver !== 'custom');
    if (b.dataset.ver !== 'custom') $('vmCustomVer').value = '';
  });
  $('modelSeg').addEventListener('click', e => {
    const b = e.target.closest('.seg-btn');
    if (!b) return;
    $$('#modelSeg .seg-btn').forEach(x => x.classList.toggle('on', x === b));
  });

  /* ROM 上传 + 实时检测（生产走原生流式检测，测试回退 JS 解析） */
  function pickRom() { B.pickFile('rom', ''); }
  function onFileReady(kind, path, name, size, target) {
    if (kind === 'rom') {
      wizard.rom = { path: path, name: name, size: size, bits: 'unknown', type: 'unknown', note: '', ok: false, det: null };
      $('romDetectArea').classList.remove('hidden');
      $('romHint').textContent = '';
      $('romUploadCard').classList.add('hidden');
      analyzeRom(path, name, size);
    } else if (kind === 'bootanim' && curVm) {
      curVm.animPath = path;
      curVm.anim = 'custom';
      $('animHint').textContent = '已选择：自定义图片（' + name + '）';
      toast('自定义开机动画已导入');
    } else if (kind === 'sendfile' && curVm) {
      toast('已从真机发送「' + name + '」到虚拟机');
      if (curScreen === 'files') renderFiles();
    }
  }
  function onFileError(kind, msg) {
    if (kind === 'rom') {
      $('romUploadCard').classList.remove('hidden');
      $('romHint').textContent = msg;
    } else {
      toast(msg);
    }
  }

  async function analyzeRom(path, name, size) {
    const card = $('romDetectCard');
    card.innerHTML = '<div class="detect-note">正在实时检测镜像…</div>';
    let det;
    /* 原生流式检测（大文件安全，生产） */
    if (B.analyzeRomNative) {
      try {
        det = JSON.parse(B.analyzeRomNative(path));
        det.bitsLabel = bitsLabel(det.bits);
        if (det.ok && size > 400 * 1048576) det.note += '（提示：文件超过 400MB，商店镜像均 ≤400MB）';
      } catch (e) {
        det = { ok: false, type: '检测异常', bits: 'unknown', bitsLabel: '未知', note: '检测异常：' + e.message };
      }
      wizard.rom.bits = det.bits;
      wizard.rom.type = det.type;
      wizard.rom.note = det.note;
      wizard.rom.ok = det.ok;
      wizard.rom.det = det;
      renderRomDetect(det, name, size);
      return;
    }
    /* 浏览器回退（测试 / 小文件） */
    let bytes;
    try {
      const url = (path.indexOf('file://') === 0 || path.indexOf('http') === 0) ? path : 'file://' + encodeURI(path);
      const resp = await fetch(url);
      if (!resp.ok) throw new Error('HTTP ' + resp.status);
      bytes = new Uint8Array(await resp.arrayBuffer());
    } catch (e) {
      card.innerHTML = '<div class="detect-note detect-bad">读取文件失败：' + esc(e.message) + '</div>';
      return;
    }
    det = detectFile(bytes, name, size);
    wizard.rom.bits = det.bits;
    wizard.rom.type = det.type;
    wizard.rom.note = det.note;
    wizard.rom.ok = det.ok;
    wizard.rom.det = det;
    renderRomDetect(det, name, size);
  }

  function renderRomDetect(det, name, size) {
    const card = $('romDetectCard');
    const match = det.ok ? (det.bits === 'unknown' || det.bits === 'both' || det.bits === wizard.bits ? 'match' : 'mismatch') : 'no';
    let matchHtml = '';
    if (det.ok) {
      if (match === 'match') matchHtml = '<div class="detect-row"><span>位数匹配</span><span class="detect-ok">✓ 与当前 ' + wizard.bits + ' 位虚拟机匹配</span></div>';
      else matchHtml = '<div class="detect-row"><span>位数匹配</span><span class="detect-bad">✗ 不匹配（ROM 为 ' + det.bits + ' 位，虚拟机为 ' + wizard.bits + ' 位）</span></div>' +
        '<button class="btn btn-primary btn-sm" style="margin-top:10px;width:100%" onclick="ETVM.forceBits(\'' + det.bits + '\')">一键切换虚拟机位数为 ' + det.bits + ' 位</button>';
    }
    card.innerHTML = `
      <div class="detect-row"><span>文件名称</span><span>${esc(name)}</span></div>
      <div class="detect-row"><span>文件大小</span><span>${fmtSize(size)}</span></div>
      <div class="detect-row"><span>系统类型</span><span class="${det.ok ? 'detect-ok' : 'detect-bad'}">${esc(det.type)}</span></div>
      <div class="detect-row"><span>系统位数</span><span class="${det.bits === 'unknown' ? 'detect-warn' : 'detect-ok'}">${esc(det.bitsLabel)}</span></div>
      ${matchHtml}
      <div class="detect-note">${esc(det.note)}</div>`;
    const next = $('romNextBtn');
    next.disabled = !det.ok || match === 'mismatch';
  }
  function forceBits(bits) {
    if (bits !== '32' && bits !== '64') return;
    wizard.bits = bits;
    $$('#bitsSeg .seg-btn').forEach(x => x.classList.toggle('on', x.dataset.bits === bits));
    if (wizard.rom && wizard.rom.det) renderRomDetect(wizard.rom.det, wizard.rom.name, wizard.rom.size);
  }
  function refreshRomMatch() {
    if (!wizard.rom || !wizard.rom.det) return;
    renderRomDetect(wizard.rom.det, wizard.rom.name, wizard.rom.size);
  }
  function setBitsUI(bits) {
    if (bits !== '32' && bits !== '64') return;
    wizard.bits = bits;
    $$('#bitsSeg .seg-btn').forEach(x => x.classList.toggle('on', x.dataset.bits === bits));
  }

  /* ---------------- 权限 ---------------- */
  function grant(kind) {
    if (B.hasPermission(kind)) {
      wizard.perms[kind] = true;
      refreshPerms();
      toast('该权限已授权');
      return;
    }
    B.requestPermission(kind);
  }
  function grantAll() {
    ['storage', 'overlay', 'install', 'notify'].forEach((k, i) => {
      setTimeout(() => {
        if (!B.hasPermission(k)) B.requestPermission(k);
        else {
          wizard.perms[k] = true;
          refreshPerms();
        }
      }, i * 900);
    });
  }
  function onPermissionResult(kind, ok) {
    wizard.perms[kind] = ok;
    refreshPerms();
    if (ok) toast('权限已授予');
  }
  function refreshPerms() {
    ['storage', 'overlay', 'install', 'notify'].forEach(k => {
      const item = document.querySelector('.perm-item[data-perm="' + k + '"]');
      if (!item) return;
      const ok = wizard.perms[k] || B.hasPermission(k);
      if (ok) wizard.perms[k] = true;
      const btn = item.querySelector('button');
      if (ok) {
        btn.textContent = '已授权';
        btn.disabled = true;
        btn.className = 'btn btn-sm perm-ok';
      } else {
        btn.textContent = '授权';
        btn.disabled = false;
        btn.className = 'btn btn-sm';
      }
    });
    const next = $('permNextBtn');
    next.disabled = !(wizard.perms.storage && wizard.perms.overlay && wizard.perms.install);
  }

  /* ---------------- 创建 ---------------- */
  function renderSummary() {
    const r = wizard.rom || {};
    $('wizardSummary').innerHTML = `
      <div class="detect-row"><span>虚拟机名称</span><span>${esc(wizard.name)}</span></div>
      <div class="detect-row"><span>系统位数</span><span>${wizard.bits} 位</span></div>
      <div class="detect-row"><span>Android 版本</span><span>${esc(wizard.ver)}</span></div>
      <div class="detect-row"><span>机型模板</span><span>${esc(wizard.model)}</span></div>
      <div class="detect-row"><span>ROM 镜像</span><span>${esc(r.name || '无')} · ${esc(r.bits ? bitsLabel(r.bits) : '未知')}</span></div>
      <div class="detect-row"><span>权限授权</span><span>存储 / 悬浮窗 / 安装 / 通知</span></div>`;
  }
  function createVm() {
    const vm = {
      id: Date.now(),
      name: wizard.name,
      bits: wizard.bits,
      ver: wizard.ver,
      model: wizard.model,
      rom: { name: wizard.rom.name, size: wizard.rom.size, bits: wizard.rom.bits },
      version: 'ET-OS 14.0 (2026.09)',
      fps: 60,
      anim: 'ET 经典',
      animPath: '',
      gapps: false,
      xposed: false,
      root: false,
      firstBoot: true,
      installed: [],
      created: Date.now()
    };
    vms.push(vm);
    save();
    toast('虚拟机「' + vm.name + '」创建成功');
    goHome();
  }

  /* ---------------- 设置 ---------------- */
  function openSettings(id) {
    curVm = findVm(id);
    if (!curVm) return;
    $('settingsTitle').textContent = curVm.name + ' · 设置';
    $('fpsRange').value = curVm.fps || 60;
    $('fpsVal').textContent = curVm.fps || 60;
    $$('#animSeg .seg-btn').forEach(x => x.classList.toggle('on', x.dataset.anim === (curVm.animPath ? 'custom' : (curVm.anim || 'ET 经典'))));
    $('animHint').textContent = '已选择：' + (curVm.animPath ? '自定义图片' : (curVm.anim || 'ET 经典'));
    $('optGapps').checked = !!curVm.gapps;
    $('optXposed').checked = !!curVm.xposed;
    $('optRoot').checked = !!curVm.root;
    $('vmVersion').value = curVm.version || '';
    show('settings');
  }
  $('fpsRange').addEventListener('input', e => { $('fpsVal').textContent = e.target.value; });
  $('animSeg').addEventListener('click', e => {
    const b = e.target.closest('.seg-btn');
    if (!b) return;
    $$('#animSeg .seg-btn').forEach(x => x.classList.toggle('on', x === b));
    if (b.dataset.anim === 'custom') {
      B.pickFile('bootanim', '');
    } else {
      if (curVm) curVm.anim = b.dataset.anim;
      $('animHint').textContent = '已选择：' + b.dataset.anim + '（内置动画）';
    }
  });
  function saveSettings() {
    if (!curVm) return;
    curVm.fps = parseInt($('fpsRange').value, 10);
    curVm.gapps = $('optGapps').checked;
    curVm.xposed = $('optXposed').checked;
    curVm.root = $('optRoot').checked;
    curVm.version = $('vmVersion').value.trim() || 'ET-OS 14.0 (2026.09)';
    const animSel = $$('#animSeg .seg-btn.on')[0];
    if (animSel && animSel.dataset.anim !== 'custom') curVm.anim = animSel.dataset.anim;
    save();
    toast('设置已保存');
    goHome();
  }

  /* ---------------- 开机（首次安装 ROM / 二次快速启动） ---------------- */
  function startVm(id) {
    curVm = findVm(id);
    if (!curVm) return;
    const wrap = $('bootWrap');
    wrap.innerHTML = '';
    if (curVm.animPath) {
      const animSrc = (curVm.animPath.indexOf('file://') === 0 || curVm.animPath.indexOf('http') === 0) ? curVm.animPath : 'file://' + encodeURI(curVm.animPath);
      wrap.innerHTML = '<img class="boot-custom-img" src="' + animSrc + '">' +
        '<div class="boot-bar"><div class="boot-progress" id="bootProgress"></div></div><div class="boot-lines" id="bootLines"></div>';
    } else {
      wrap.innerHTML = '<div class="boot-logo" id="bootLogo">ET</div>' +
        '<div class="boot-bar"><div class="boot-progress" id="bootProgress"></div></div><div class="boot-lines" id="bootLines"></div>';
      const logo = wrap.querySelector('.boot-logo');
      if (curVm.anim === '极光粒子') { logo.style.background = 'linear-gradient(135deg,#7c4dff,#00e5ff)'; logo.textContent = '◆'; }
      else if (curVm.anim === '极简线条') { logo.style.background = '#0e1526'; logo.style.border = '3px solid #00e5ff'; logo.style.boxShadow = 'none'; }
    }
    show('boot');
    bootSequence();
  }
  function bootSequence() {
    const first = curVm.firstBoot !== false;
    let lines;
    let stepMs;
    if (first) {
      lines = [
        '正在加载 ET 虚拟机内核…',
        '正在初始化系统镜像…',
        '首次启动：正在安装 ROM「' + (curVm.rom ? curVm.rom.name : '自定义') + '」…',
        '正在校验系统位数（' + curVm.bits + ' 位）…'
      ];
      if (curVm.gapps) lines.push('正在安装 Google 服务框架 (GApps)…');
      if (curVm.xposed) lines.push('正在注入 Xposed 框架…');
      if (curVm.root) lines.push('正在启用 Root 工具…');
      lines.push('正在应用 ' + (curVm.fps || 60) + 'Hz 刷新率…');
      lines.push('正在自动安装「连接储存」共享组件…');
      lines.push('正在优化应用缓存…');
      lines.push('首次启动完成，进入系统');
      stepMs = 700;
    } else {
      lines = [
        '正在快速启动…',
        '正在加载系统镜像缓存…',
        '正在校验真机共享文件夹…',
        '正在应用 ' + (curVm.fps || 60) + 'Hz 刷新率…',
        '开机完成，进入系统'
      ];
      stepMs = 460;
    }
    const box = $('bootLines');
    if (!box) return;
    let step = 0;
    const total = lines.length;
    const progress = $('bootProgress');
    const timer = setInterval(() => {
      if (step < total) {
        box.innerHTML = lines.slice(0, step).map(l => '<div class="boot-line done">✓ ' + esc(l) + '</div>').join('') +
          '<div class="boot-line now">▸ ' + esc(lines[step]) + '</div>';
        progress.style.width = Math.round(((step + 0.5) / total) * 100) + '%';
        step++;
        vibrate(12);
      } else {
        clearInterval(timer);
        progress.style.width = '100%';
        /* 首次启动：自动安装「连接储存」 */
        if (first) {
          curVm.installed = curVm.installed || [];
          if (!curVm.installed.some(a => a.pkg === 'com.et.storage')) {
            curVm.installed.unshift({ label: '连接储存', pkg: 'com.et.storage', abi: '内置 · 真机共享', color: 'tile-orange' });
          }
          curVm.firstBoot = false;
          save();
        }
        setTimeout(() => { renderVm(); show('vm'); }, 420);
      }
    }, stepMs);
  }

  /* ---------------- 虚拟机桌面 ---------------- */
  function renderVm() {
    if (!curVm) return;
    $('vmInfoBadge').textContent = curVm.bits + ' 位 · ' + (curVm.fps || 60) + 'Hz';
    $('vmGreet').textContent = curVm.name + ' · 一切皆可运行';
    const grid = $('appGrid');
    const sysApps = [
      { label: '系统设置', tile: 'tile-green', key: 'set' },
      { label: 'ET 浏览器', tile: 'tile-blue', key: 'browser' },
      { label: '连接储存', tile: 'tile-orange', key: 'files' },
      { label: '相机', tile: 'tile-red', key: 'camera' },
      { label: '相册', tile: 'tile-purple', key: 'gallery' }
    ];
    let html = sysApps.map(a => `<div class="app-item" onclick="ETVM.openSysApp('${a.key}')"><span class="tile ${a.tile}">${firstChar(a.label)}</span><b>${a.label}</b></div>`).join('');
    (curVm.installed || []).forEach(a => {
      html += `<div class="app-item" onclick="ETVM.openInstalled('${esc(a.pkg)}')"><span class="tile ${a.color || 'tile-cyan'}">${firstChar(a.label)}</span><b>${esc(a.label)}</b></div>`;
    });
    grid.innerHTML = html;
  }
  function openSysApp(key) {
    if (key === 'files') { openFiles(); return; }
    const tips = { set: '打开系统设置 · ' + curVm.ver, browser: 'ET 浏览器 · 快速浏览', camera: '相机 · 调用虚拟机摄像头', gallery: '相册 · 查看媒体文件' };
    toast(tips[key] || '正在打开…');
    vibrate(20);
  }
  function openInstalled(pkg) {
    if (pkg === 'com.et.storage') { openFiles(); return; }
    const a = (curVm.installed || []).find(x => x.pkg === pkg);
    toast('正在启动 ' + (a ? a.label : pkg));
    vibrate(20);
  }
  function shutdownVm() {
    toast('正在关机…');
    setTimeout(() => {
      goHome();
      if (B.hasPermission('overlay')) B.stopFloat();
    }, 900);
  }

  /* 悬浮窗 */
  function toggleFloat() {
    if (B.hasPermission('overlay')) {
      B.startFloat();
      toast('悬浮控制球已开启，可拖动使用');
    } else {
      toast('请先授权悬浮窗权限');
      B.requestPermission('overlay');
    }
  }
  function onFloatError(msg) { toast(msg); }
  function onFloatKey(key) {
    vibrate(18);
    switch (key) {
      case 'home': if (curScreen === 'vm') renderVm(); toast('返回虚拟机桌面'); break;
      case 'back': toast('返回上一页'); break;
      case 'menu': toast('最近任务'); break;
      case 'volup': toast('音量 +'); break;
      case 'voldown': toast('音量 -'); break;
    }
  }

  /* ---------------- 应用中心 ---------------- */
  function openStore() {
    if (!curVm) { toast('请先创建并启动一台虚拟机'); return; }
    $('storeSub').textContent = '当前虚拟机：' + curVm.name + ' · ' + curVm.bits + ' 位';
    renderStore();
    show('store');
  }
  function renderStore() {
    const list = $('storeList');
    const apps = curVm.installed || [];
    $('storeCount').textContent = apps.length;
    if (!apps.length) {
      list.innerHTML = '<div class="empty">还没有导入应用，点击下方按钮从真机导出</div>';
      return;
    }
    list.innerHTML = apps.map(a => `
      <div class="store-item">
        <span class="tile ${a.color || 'tile-cyan'}" style="width:40px;height:40px;font-size:15px">${firstChar(a.label)}</span>
        <div><b>${esc(a.label)}</b><i>${esc(a.pkg)} · ${a.abi || '兼容'}</i></div>
        <span class="badge badge-ok">已安装</span>
      </div>`).join('');
  }
  function importFromDevice() {
    importCache = [];
    try { importCache = JSON.parse(B.getInstalledApps() || '[]'); } catch (e) { importCache = []; }
    if (!importCache.length) { toast('未找到可导出的应用'); return; }
    filterApps();
    $('appPickerOverlay').classList.remove('hidden');
  }
  function filterApps() {
    const q = ($('appSearch').value || '').trim().toLowerCase();
    const list = $('appList');
    const filtered = importCache.filter(a => !q || a.label.toLowerCase().indexOf(q) > -1 || a.pkg.toLowerCase().indexOf(q) > -1);
    if (!filtered.length) { list.innerHTML = '<div class="empty">无匹配应用</div>'; return; }
    list.innerHTML = filtered.map(a => `
      <div class="app-pick" onclick="ETVM.pickApp('${esc(a.pkg)}')">
        <span class="tile ${colorFor(a.pkg)}">${firstChar(a.label)}</span>
        <div><b>${esc(a.label)}</b><i>${esc(a.pkg)} · ${fmtSize(a.size)}${a.system ? ' · 系统应用' : ''}</i></div>
      </div>`).join('');
  }
  function closePicker() { $('appPickerOverlay').classList.add('hidden'); }
  async function pickApp(pkg) {
    const app = importCache.find(a => a.pkg === pkg);
    if (!app) return;
    if (pkg === 'com.et.vm') { toast('不能导入 ET 虚拟机自身'); return; }
    if ((curVm.installed || []).some(x => x.pkg === pkg)) { toast('该应用已在虚拟机中'); return; }
    closePicker();
    $('compatOverlay').classList.remove('hidden');
    $('compatTitle').textContent = '正在检测兼容性…';
    $('compatBody').innerHTML = '<div class="detect-note">正在读取应用包并实时检测位数兼容性…</div>';
    $('compatInstallBtn').classList.add('hidden');
    const path = B.importApk(app.apk, String(curVm.id));
    if (!path) { $('compatBody').innerHTML = '<div class="detect-note detect-bad">读取应用包失败</div>'; return; }
    try {
      const url = (path.indexOf('file://') === 0 || path.indexOf('http') === 0) ? path : 'file://' + encodeURI(path);
      const resp = await fetch(url);
      const bytes = new Uint8Array(await resp.arrayBuffer());
      const chk = checkApkCompat(bytes);
      pendingInstall = { label: app.label, pkg: app.pkg, abi: chk.reason, ok: chk.ok };
      $('compatTitle').textContent = '兼容性检测结果';
      $('compatBody').innerHTML = `
        <div class="detect-row"><span>应用名称</span><span>${esc(app.label)}</span></div>
        <div class="detect-row"><span>包名</span><span>${esc(app.pkg)}</span></div>
        <div class="detect-row"><span>原生库位数</span><span>${esc(chk.detail)}</span></div>
        <div class="detect-row"><span>与虚拟机（${curVm.bits} 位）</span><span class="${chk.ok ? 'detect-ok' : 'detect-bad'}">${chk.ok ? '✓ 兼容' : '✗ 不兼容'}</span></div>
        <div class="detect-note">${esc(chk.reason)}</div>`;
      if (chk.ok) $('compatInstallBtn').classList.remove('hidden');
    } catch (e) {
      $('compatBody').innerHTML = '<div class="detect-note detect-bad">读取应用包失败：' + esc(e.message) + '</div>';
    }
  }
  function confirmInstall() {
    if (!pendingInstall) return;
    curVm.installed = curVm.installed || [];
    curVm.installed.push({ label: pendingInstall.label, pkg: pendingInstall.pkg, abi: pendingInstall.abi, color: colorFor(pendingInstall.pkg) });
    save();
    toast('「' + pendingInstall.label + '」已安装到虚拟机');
    closeCompat();
    renderStore();
    renderVm();
  }
  function closeCompat() { $('compatOverlay').classList.add('hidden'); pendingInstall = null; }

  /* ---------------- ROM 商店 ---------------- */
  async function loadCatalog() {
    /* 生产：原生读 assets 目录（WebView 的 file:// fetch 会被拦截导致列表刷新失败） */
    if (B.getCatalog) {
      try {
        const raw = B.getCatalog();
        if (raw) {
          catalog = (JSON.parse(raw).roms) || [];
          return true;
        }
      } catch (e) { catalog = []; return false; }
    }
    try {
      const resp = await fetch('rom-catalog.json?_=' + Date.now());
      const data = await resp.json();
      catalog = data.roms || [];
      return true;
    } catch (e) {
      catalog = [];
      return false;
    }
  }
  function openRomStore() {
    renderRomStoreScreen();
    show('romstore');
  }
  function renderRomStoreScreen() {
    $('romstoreList').innerHTML = '<div class="empty">正在加载 ROM 列表…</div>';
    loadCatalog().then(() => {
      if (!catalog.length) {
        $('romstoreList').innerHTML = '<div class="empty">列表加载失败，请检查网络后点"刷新列表"</div>';
        return;
      }
      try { romDls = JSON.parse(localStorage.getItem('romDls') || '{}'); } catch (e) { romDls = {}; }
      $('romstoreList').innerHTML = catalog.map(r => {
        const dl = romDls[r.id];
        return `
        <div class="rom-card" id="romcard-${r.id}">
          <div class="rom-card-head">
            <div class="rom-icon">${firstChar(r.name)}</div>
            <div>
              <b>${esc(r.name)}</b>
              <span class="sub">${esc(r.desc || '')}</span>
            </div>
          </div>
          <div class="rom-tags">
            <span class="rom-tag tag-ver">${esc(r.ver)}</span>
            <span class="rom-tag tag-bits">${r.bits} 位</span>
            <span class="rom-tag tag-size">${r.sizeMb} MB</span>
            <span class="rom-tag tag-size" id="romstatus-${r.id}">${dl ? '已下载' : '未下载'}</span>
          </div>
          <div class="rom-card-actions">
            ${dl ? `<button class="btn btn-primary" id="romuse-${r.id}" onclick="ETVM.useRom('${r.id}')">使用此 ROM</button>` : ''}
            <button class="btn" id="rombtn-${r.id}" onclick="ETVM.downloadRom('${r.id}')">${dl ? '重新下载' : '下载 ROM'}</button>
          </div>
          <div class="rom-progress hidden" id="romprog-${r.id}"><i id="romprogbar-${r.id}"></i></div>
        </div>`;
      }).join('');
    });
  }
  function refreshCatalog() {
    toast('正在刷新列表…');
    renderRomStoreScreen();
  }
  function downloadRom(id) {
    const r = catalog.find(x => x.id === id);
    if (!r) return;
    const btn = $('rombtn-' + id);
    const prog = $('romprog-' + id);
    const bar = $('romprogbar-' + id);
    btn.textContent = '下载中…';
    btn.disabled = true;
    prog.classList.remove('hidden');
    bar.style.width = '2%';
    let dlId;
    if (B.downloadRom) {
      dlId = B.downloadRom(r.url, r.file);
    } else {
      dlId = '-1';
    }
    if (dlId === '-1' || dlId === '-2') {
      btn.textContent = '下载失败';
      btn.disabled = false;
      toast('下载启动失败，请检查网络');
      return;
    }
    clearInterval(dlTimers[id]);
    dlTimers[id] = setInterval(() => {
      let info;
      try { info = JSON.parse(B.getDownloadInfo(dlId) || '{}'); } catch (e) { info = {}; }
      if (info.done) {
        clearInterval(dlTimers[id]);
        const path = info.path || '';
        romDls[id] = { file: r.file, path: path };
        try { localStorage.setItem('romDls', JSON.stringify(romDls)); } catch (e) {}
        const st = $('romstatus-' + id);
        if (st) st.textContent = '已下载';
        bar.style.width = '100%';
        btn.textContent = '重新下载';
        btn.disabled = false;
        if (!$('romuse-' + id)) {
          const use = document.createElement('button');
          use.className = 'btn btn-primary';
          use.id = 'romuse-' + id;
          use.style.flex = '1';
          use.textContent = '使用此 ROM';
          use.onclick = () => ETVM.useRom(id);
          btn.parentElement.insertBefore(use, btn.parentElement.firstChild);
        }
        toast('「' + r.name + '」下载完成');
      } else if (info.failed) {
        clearInterval(dlTimers[id]);
        btn.textContent = '下载失败 · 重试';
        btn.disabled = false;
        toast('下载失败，请重试');
      } else if (info.total > 0) {
        const pct = Math.min(100, Math.round((info.bytes / info.total) * 100));
        bar.style.width = pct + '%';
        btn.textContent = '下载中 ' + pct + '%';
      }
    }, 1200);
  }
  function useRom(id) {
    const r = catalog.find(x => x.id === id);
    const dl = romDls[id];
    if (!r || !dl || !dl.path) { toast('ROM 尚未下载完成'); return; }
    openWizard();
    setBitsUI(r.bits);
    wizardNext();
    onFileReady('rom', dl.path, dl.file, (r.sizeMb || 0) * 1048576, '');
  }

  /* ---------------- 连接储存（真机 ↔ 虚拟机 单向共享） ---------------- */
  function openFiles() {
    if (!curVm) { toast('请先启动虚拟机'); return; }
    $('filesSub').textContent = '当前虚拟机：' + curVm.name + ' · 单向连接';
    try {
      const shared = B.getSharedDir ? B.getSharedDir() : '';
      $('sharedPathHint').textContent = shared ? '真机共享目录：' + shared + '（把文件放进去，虚拟机即可查看/复制）' : '真机可随时向虚拟机发送文件';
    } catch (e) {}
    filesTab = 'vm';
    $$('#filesTabSeg .seg-btn').forEach(x => x.classList.toggle('on', x.dataset.tab === 'vm'));
    renderFiles();
    show('files');
  }
  $('filesTabSeg').addEventListener('click', e => {
    const b = e.target.closest('.seg-btn');
    if (!b) return;
    filesTab = b.dataset.tab;
    $$('#filesTabSeg .seg-btn').forEach(x => x.classList.toggle('on', x === b));
    renderFiles();
  });
  function renderFiles() {
    const list = $('filesList');
    let arr = [];
    try {
      arr = JSON.parse(filesTab === 'vm' ? B.listVmFiles(String(curVm.id)) : B.listSharedFiles());
    } catch (e) { arr = []; }
    if (!arr.length) {
      list.innerHTML = '<div class="empty">' + (filesTab === 'vm' ? '虚拟机存储为空，可从「真机共享」复制文件' : '真机共享目录为空，点击"从真机发送"或把文件放入共享目录') + '</div>';
      return;
    }
    list.innerHTML = arr.map(f => {
      const isImg = /\.(png|jpe?g|gif|webp)$/i.test(f.name);
      const acts = filesTab === 'vm'
        ? `<button class="btn" onclick="ETVM.viewFile('${esc(f.name)}')">查看</button><button class="btn" onclick="ETVM.delFile('${esc(f.name)}')">删除</button>`
        : `<button class="btn" onclick="ETVM.viewFile('${esc(f.name)}')">查看</button><button class="btn btn-primary" onclick="ETVM.copyFile('${esc(f.name)}')">复制到虚拟机</button>`;
      return `
      <div class="file-row">
        <span class="tile ${isImg ? 'tile-purple' : 'tile-blue'}">${isImg ? '图' : '文'}</span>
        <div style="min-width:0"><b>${esc(f.name)}</b><i>${fmtSize(f.size)}${f.dir ? ' · 目录' : ''}</i></div>
        <div class="file-actions">${acts}</div>
      </div>`;
    }).join('');
  }
  function sendFileFromDevice() {
    if (!curVm) return;
    B.pickFile('sendfile', String(curVm.id));
  }
  function viewFile(name) {
    if (name.indexOf('/') > -1) { toast('暂不支持子目录'); return; }
    let path = '';
    try {
      const arr = JSON.parse(filesTab === 'vm' ? B.listVmFiles(String(curVm.id)) : B.listSharedFiles());
      const f = arr.find(x => x.name === name);
      path = f ? f.path : '';
    } catch (e) {}
    if (!path) { toast('无法定位文件'); return; }
    $('viewerOverlay').classList.remove('hidden');
    $('viewerTitle').textContent = '查看 · ' + name;
    const body = $('viewerBody');
    const isImg = /\.(png|jpe?g|gif|webp)$/i.test(name);
    if (isImg) {
      body.innerHTML = '<div class="detect-note">正在加载图片…</div>';
      let b64 = '';
      try { b64 = B.readFileBase64 ? B.readFileBase64(path, 2 * 1024 * 1024) : ''; } catch (e) { b64 = ''; }
      body.innerHTML = b64 ? '<img src="data:image/*;base64,' + b64 + '" style="width:100%;border-radius:10px">' : '<div class="detect-note detect-bad">图片过大，无法预览</div>';
      return;
    }
    body.innerHTML = '<div class="detect-note">正在读取…</div>';
    let txt = '';
    try { txt = B.readFileText ? B.readFileText(path, 512) : ''; } catch (e) { txt = ''; }
    if (txt === 'BINARY') body.innerHTML = '<div class="detect-note detect-warn">二进制文件，暂不支持文本预览（可复制到虚拟机使用）</div>';
    else body.innerHTML = '<div class="detect-note">' + esc(txt.length > 0 ? txt : '(空文件)') + '</div>';
  }
  function closeViewer() { $('viewerOverlay').classList.add('hidden'); }
  function copyFile(name) {
    if (!curVm) return;
    let ok = false;
    try { ok = B.copyFromShared ? B.copyFromShared(String(curVm.id), name) : false; } catch (e) { ok = false; }
    toast(ok ? '已复制到虚拟机存储' : '复制失败（可能已存在同名文件）');
    renderFiles();
  }
  function delFile(name) {
    if (!confirm('确定删除虚拟机中的「' + name + '」吗？')) return;
    let ok = false;
    try { ok = B.deleteVmFile ? B.deleteVmFile(String(curVm.id), name) : false; } catch (e) { ok = false; }
    toast(ok ? '已删除' : '删除失败');
    renderFiles();
  }

  /* ---------------- 开发者 & 关于 ---------------- */
  function openDeveloper() { show('developer'); }
  function devToast(msg) { toast(msg); vibrate(12); }
  $('animScaleSeg').addEventListener('click', e => {
    const b = e.target.closest('.seg-btn');
    if (!b) return;
    $$('#animScaleSeg .seg-btn').forEach(x => x.classList.toggle('on', x === b));
    toast('动画缩放：' + b.dataset.scale);
  });
  function renderAbout() {
    const info = JSON.parse(B.getDeviceInfo());
    const perms = [
      ['存储 & 麦克风', B.hasPermission('storage')],
      ['悬浮窗', B.hasPermission('overlay')],
      ['安装未知应用', B.hasPermission('install')],
      ['通知', B.hasPermission('notify')]
    ];
    $('aboutCard').innerHTML = `
      <div class="detect-row"><span>设备型号</span><span>${esc(info.brand)} ${esc(info.model)}</span></div>
      <div class="detect-row"><span>Android 版本</span><span>${esc(info.android)} (API ${info.sdk})</span></div>
      <div class="detect-row"><span>CPU 架构</span><span>${esc(info.abi)}</span></div>
      <div class="detect-row"><span>应用版本</span><span>ET虚拟机 v2.0.0</span></div>`;
    $('aboutPerms').innerHTML = perms.map(p => `
      <div class="perm-item"><div><b>${p[0]}</b></div><span class="${p[1] ? 'perm-ok' : ''}">${p[1] ? '✓ 已授权' : '未授权'}</span></div>`).join('');
  }

  /* ---------------- 全局按键 ---------------- */
  function onBack() {
    if (!$('appPickerOverlay').classList.contains('hidden')) { closePicker(); return; }
    if (!$('compatOverlay').classList.contains('hidden')) { closeCompat(); return; }
    if (!$('viewerOverlay').classList.contains('hidden')) { closeViewer(); return; }
    if (curScreen === 'wizard') {
      if (wizard.step > 1) { wizardBack(); return; }
      backHome();
    } else if (curScreen === 'settings' || curScreen === 'about' || curScreen === 'romstore') backHome();
    else if (curScreen === 'store' || curScreen === 'developer' || curScreen === 'files') gotoVm();
    else if (curScreen === 'vm') { shutdownVm(); }
  }
  function backHome() { show('home'); }
  function gotoVm() { if (curVm) { renderVm(); show('vm'); } else backHome(); }

  /* ---------------- 时钟 ---------------- */
  setInterval(() => {
    const d = new Date();
    const t = String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
    const el = $('vmClock');
    if (el) el.textContent = t;
  }, 1000);

  /* ---------------- ZIP 解析（浏览器回退 & APK 检测） ---------------- */
  function parseZip(u8) {
    const view = new DataView(u8.buffer, u8.byteOffset, u8.byteLength);
    let eocd = -1;
    for (let i = u8.length - 22; i >= 0 && i > u8.length - 70000; i--) {
      if (u8[i] === 0x50 && u8[i + 1] === 0x4b && u8[i + 2] === 0x05 && u8[i + 3] === 0x06) { eocd = i; break; }
    }
    if (eocd < 0) return [];
    const total = view.getUint16(eocd + 10, true);
    const cdOff = view.getUint32(eocd + 16, true);
    const entries = [];
    let p = cdOff;
    for (let i = 0; i < total; i++) {
      if (p + 46 > u8.length) break;
      if (!(u8[p] === 0x50 && u8[p + 1] === 0x4b && u8[p + 2] === 0x01 && u8[p + 3] === 0x02)) break;
      const nlen = view.getUint16(p + 28, true);
      const elen = view.getUint16(p + 30, true);
      const clen = view.getUint16(p + 32, true);
      let name = '';
      for (let j = 0; j < nlen; j++) name += String.fromCharCode(u8[p + 46 + j]);
      entries.push(name);
      p += 46 + nlen + elen + clen;
    }
    return entries;
  }
  function detectFile(u8, name, size) {
    const lower = (name || '').toLowerCase();
    const isPE = u8.length > 2 && u8[0] === 0x4D && u8[1] === 0x5A;
    const entries = parseZip(u8);
    const has = re => entries.some(n => re.test(n.toLowerCase()));
    let note = '';
    if (isPE) {
      return { type: 'Windows 系统镜像（PE 格式）', bits: 'unknown', bitsLabel: '—', ok: false, note: '检测到 Windows 引导头 (MZ/PE)。ET虚拟机仅支持 Android 系统，已拒绝该镜像。' };
    }
    const winMarkers = has(/windows/i) || (has(/efi\/boot/i) && has(/sources\/install\.wim/i)) || lower.indexOf('windows') > -1;
    if (winMarkers) {
      return { type: 'Windows 系统镜像', bits: 'unknown', bitsLabel: '—', ok: false, note: '检测到 Windows 安装文件（install.wim/EFI）。仅支持 Android 系统，已拒绝。' };
    }
    const androidMarkers = [
      /(^|\/)system\.img($|[.\s])/, /(^|\/)system\/build\.prop/, /build\.prop/,
      /(^|\/)boot\.img/, /(^|\/)vendor\.img/, /META-INF\/com\/google\/android/,
      /payload\.bin/, /(^|\/)system\/framework/, /(^|\/)vendor\/build\.prop/,
      /(^|\/)apex\//, /(^|\/)prebuilt/, /update\.zip/, /ota/, /(^|\/)product\/build\.prop/
    ];
    const isApk = has(/AndroidManifest\.xml/) && has(/classes\.dex/);
    const isAndroid = androidMarkers.some(re => has(re)) || lower.indexOf('android') > -1 || lower.indexOf('rom') > -1 || lower.indexOf('miui') > -1 || lower.indexOf('lineage') > -1 || lower.indexOf('pixel') > -1 || lower.indexOf('coloros') > -1 || lower.indexOf('oxygen') > -1 || lower.indexOf('emui') > -1 || lower.indexOf('harmony') > -1;
    const has64 = has(/lib\/arm64-v8a\//) || has(/lib\/x86_64\//) || has(/arm64/) || has(/aarch64/) || lower.indexOf('arm64') > -1 || lower.indexOf('a64') > -1 || lower.indexOf('_64') > -1;
    const has32 = has(/lib\/armeabi-v7a\//) || has(/lib\/arm\//) || has(/lib\/x86\//) || has(/armeabi/) || has(/armv7/) || lower.indexOf('armv7') > -1 || lower.indexOf('-32') > -1 || lower.indexOf('i686') > -1;
    let bits = 'unknown';
    if (has64 && has32) bits = 'both';
    else if (has64) bits = '64';
    else if (has32) bits = '32';
    if (!isAndroid && !isApk) {
      note = entries.length ? '未发现 Android 系统镜像标记（system.img / build.prop / boot.img 等）。请确认上传的是 Android ROM 或刷机包。' : '该文件不是有效的 ZIP/IMG 镜像包，无法识别为 Android 系统。';
      return { type: '未知系统包', bits: bits, bitsLabel: bitsLabel(bits), ok: false, note: note };
    }
    note = '检测到 Android 系统镜像。';
    if (bits === '64') note += '镜像包含 64 位 ABI（arm64-v8a / x86_64）。';
    else if (bits === '32') note += '镜像仅包含 32 位 ABI（armeabi-v7a / x86）。';
    else if (bits === 'both') note += '镜像同时包含 32/64 位 ABI，双向兼容。';
    else note += '未发现明确 ABI 目录，按通用处理。';
    note += entries.length ? ' · 扫描到 ' + entries.length + ' 个文件条目。' : '';
    return { type: isApk ? 'Android 应用包 (APK)' : 'Android 系统镜像 / ROM', bits: bits, bitsLabel: bitsLabel(bits), ok: true, note: note };
  }
  function checkApkCompat(u8) {
    const entries = parseZip(u8);
    const has = re => entries.some(n => re.test(n.toLowerCase()));
    const has64 = has(/lib\/arm64-v8a\//) || has(/lib\/x86_64\//);
    const has32 = has(/lib\/armeabi-v7a\//) || has(/lib\/arm\//) || has(/lib\/x86\//);
    const vmBits = curVm ? curVm.bits : '64';
    if (!has64 && !has32) {
      return { ok: true, detail: '无原生库（纯 Java 应用）', reason: '该应用不含原生 .so 库，任意位数虚拟机均可运行，兼容。' };
    }
    if (has64 && has32) {
      return { ok: true, detail: 'arm64 + armv7 混合', reason: '应用同时包含 64 位与 32 位原生库，与 ' + vmBits + ' 位虚拟机匹配，兼容。' };
    }
    if (has64) {
      return vmBits === '64'
        ? { ok: true, detail: '仅 arm64-v8a / x86_64', reason: '应用包含 64 位原生库，与 ' + vmBits + ' 位虚拟机匹配，兼容。' }
        : { ok: false, detail: '仅 arm64-v8a / x86_64', reason: '应用仅包含 64 位原生库，与当前 32 位虚拟机不匹配。请创建 64 位虚拟机后导入。' };
    }
    return vmBits === '32'
      ? { ok: true, detail: '仅 armeabi-v7a / x86', reason: '应用包含 32 位原生库，与 ' + vmBits + ' 位虚拟机匹配，兼容。' }
      : { ok: false, detail: '仅 armeabi-v7a / x86', reason: '应用仅包含 32 位原生库（armeabi-v7a/x86），与当前 64 位虚拟机不匹配。可切换到 32 位虚拟机，或选择 64 位版本应用。' };
  }

  /* ---------------- 首页菜单 ---------------- */
  $$('#homeMenu .menu-item').forEach(el => {
    el.addEventListener('click', () => {
      const go = el.dataset.go;
      if (go === 'wizard') openWizard();
      else if (go === 'romstore') openRomStore();
      else if (go === 'store') { if (curVm) openStore(); else toast('请先创建并启动一台虚拟机'); }
      else if (go === 'about') { renderAbout(); show('about'); }
      else if (go === 'machines') renderHome();
    });
  });

  /* ---------------- 启动 ---------------- */
  document.addEventListener('DOMContentLoaded', () => {
    const info = JSON.parse(B.getDeviceInfo());
    typewriter($('welcomeTyped'), '欢迎进入 ET 虚拟机 · ' + info.abi + '\nROM 商店 · 共享文件夹 · 一切皆可运行', 40);
  });

  /* ---------------- 导出 ---------------- */
  window.ETVM = {
    goHome: goHome,
    openWizard: openWizard,
    wizardNext: wizardNext,
    wizardBack: wizardBack,
    backHome: backHome,
    pickRom: pickRom,
    grant: grant,
    grantAll: grantAll,
    createVm: createVm,
    forceBits: forceBits,
    startVm: startVm,
    openSettings: openSettings,
    saveSettings: saveSettings,
    removeVm: removeVm,
    toggleFloat: toggleFloat,
    openStore: openStore,
    importFromDevice: importFromDevice,
    filterApps: filterApps,
    closePicker: closePicker,
    pickApp: pickApp,
    confirmInstall: confirmInstall,
    closeCompat: closeCompat,
    openDeveloper: openDeveloper,
    openSysApp: openSysApp,
    openInstalled: openInstalled,
    shutdownVm: shutdownVm,
    gotoVm: gotoVm,
    devToast: devToast,
    onFileReady: onFileReady,
    onFileError: onFileError,
    onPermissionResult: onPermissionResult,
    onFloatKey: onFloatKey,
    onFloatError: onFloatError,
    onBack: onBack,
    openRomStore: openRomStore,
    refreshCatalog: refreshCatalog,
    downloadRom: downloadRom,
    useRom: useRom,
    openFiles: openFiles,
    renderFiles: renderFiles,
    sendFileFromDevice: sendFileFromDevice,
    viewFile: viewFile,
    closeViewer: closeViewer,
    copyFile: copyFile,
    delFile: delFile
  };
})();
