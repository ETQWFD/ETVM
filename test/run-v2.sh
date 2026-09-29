#!/bin/bash
# ET虚拟机 v2.0.0 · 全状态 headless 渲染 + DOM 断言
set -e
CHROME=/usr/local/bin/chromium
ROOT=/home/user/Doubao/chats/38444626514490626/ETVM
ASSETS=$ROOT/assets
SHOTS=$ROOT/test/shots
DOMS=$ROOT/test/doms
mkdir -p $SHOTS $DOMS
URL="file://$ASSETS/index.html?test=1#"

render() {
  local state=$1 budget=$2
  $CHROME --headless=new --disable-gpu --no-sandbox --allow-file-access-from-files \
    --window-size=390,844 --hide-scrollbars --virtual-time-budget=$budget \
    --screenshot=$SHOTS/$state.png --dump-dom "$URL$state" > $DOMS/$state.html 2>/dev/null
  echo "rendered $state ($budget)"
}

render welcome 4000
render home 4000
render wizard 4000
render detect64 4000
render detectwin 4000
render detect32 4000
render detect32fix 4000
render perm 4000
render summary 4000
render settings 4000
render boot 4000
render bootfull 9500
render bootquick 4000
render vm 4000
render store 4000
render romstore 4000
render romstore-download 4000
render files 4000
render files-shared 4000
render compat64 4000
render compat32 4000
render compatjava 4000
render about 4000

echo "== python 断言 =="
/opt/python3.12/bin/python3 << 'EOF'
import os, re, json
ROOT='/home/user/Doubao/chats/38444626514490626/ETVM'
DOMS=os.path.join(ROOT,'test/doms')
SHOTS=os.path.join(ROOT,'test/shots')
def dom(s): return open(os.path.join(DOMS,s+'.html'),encoding='utf-8',errors='ignore').read()
def txt(s):
    h=dom(s)
    return re.sub(r'<[^>]+>',' ',h)
def ok(name, cond):
    print(('PASS  ' if cond else 'FAIL  ')+name)
    return cond
fails=0
def btn_attr(s, btn_id):
    m = re.search(r'<button[^>]*id="' + btn_id + r'"[^>]*>', dom(s))
    return m.group(0) if m else ''
fails += 0 if ok('welcome v2.0.0', 'v2.0.0' in dom('welcome') and 'ET虚拟机' in dom('welcome')) else 1
fails += 0 if ok('home 5 菜单项', dom('home').count('menu-item')>=5 and '还没有虚拟机' in txt('home')) else 1
fails += 0 if ok('wizard 第1步', 'wstep1' in dom('wizard') and 'hidden' not in dom('wizard').split('id="wstep1"')[1][:80]) else 1
fails += 0 if ok('detect64 匹配', '64 位 (arm64 / x86_64)' in txt('detect64') and '✓ 与当前 64 位虚拟机匹配' in txt('detect64') and 'disabled' not in btn_attr('detect64','romNextBtn')) else 1
fails += 0 if ok('detectwin 拒绝', 'Windows' in txt('detectwin') and '仅支持 Android' in txt('detectwin')) else 1
fails += 0 if ok('detect32 不匹配', '✗ 不匹配' in txt('detect32') and '一键切换' in txt('detect32')) else 1
fails += 0 if ok('detect32fix 已匹配', '✓ 与当前 32 位虚拟机匹配' in txt('detect32fix')) else 1
fails += 0 if ok('perm 全授权', txt('perm').count('已授权')>=4 and 'disabled' not in btn_attr('perm','permNextBtn')) else 1
fails += 0 if ok('summary 确认', '我的安卓机' in txt('summary') and '64 位' in txt('summary')) else 1
fails += 0 if ok('settings 120Hz+三勾选', 'DIAG gapps=true xposed=true root=true fps=120' in dom('settings')) else 1
fails += 0 if ok('boot 首次安装 ROM', 'DIAG midboot=true romline=true' in dom('boot') and '首次启动' in txt('boot')) else 1
fails += 0 if ok('bootfull 首启后自动装连接储存', '连接储存' in txt('bootfull') and 'vm-share' in dom('bootfull') and '120Hz' in txt('bootfull')) else 1
fails += 0 if ok('bootquick 二次快速启动', 'DIAG quick=true' in dom('bootquick') and '快速启动' in txt('bootquick')) else 1
fails += 0 if ok('vm 桌面', '连接储存' in txt('vm') and 'vm-share' in dom('vm') and dom('vm').count('dock-btn')>=5 and '应用中心' in txt('vm')) else 1
fails += 0 if ok('store 应用中心', 'ET 浏览器' in txt('store') and '我的安卓机' in txt('store')) else 1
fails += 0 if ok('romstore 2 条目', 'DIAG cards=2 status1=未下载' in dom('romstore')) else 1
fails += 0 if ok('romstore 下载完成', 'DIAG status1=已下载 usebtn=yes' in dom('romstore-download') and '使用此 ROM' in txt('romstore-download')) else 1
fails += 0 if ok('files 虚拟机存储', 'DIAG rows=2' in dom('files') and 'hello.txt' in txt('files') and 'sharedPathHint' in dom('files')) else 1
fails += 0 if ok('files-shared 真机共享', 'DIAG rows=2 actions=4' in dom('files-shared') and 'shared-note.txt' in txt('files-shared') and '复制到虚拟机' in txt('files-shared')) else 1
fails += 0 if ok('compat64 兼容安装', '✓ 兼容' in txt('compat64') and 'hidden' not in btn_attr('compat64','compatInstallBtn')) else 1
fails += 0 if ok('compat32 拒绝', '✗ 不兼容' in txt('compat32') and '可切换到 32 位虚拟机' in txt('compat32')) else 1
fails += 0 if ok('compatjava 纯Java', '✓ 兼容' in txt('compatjava') and '纯 Java' in txt('compatjava')) else 1
fails += 0 if ok('about v2.0.0', 'ET虚拟机 v2.0.0' in txt('about')) else 1
# 截图存在性
for s in ['welcome','home','detect64','detect32','detect32fix','perm','summary','settings','boot','bootfull','bootquick','vm','store','romstore','romstore-download','files','files-shared','compat64','about']:
    if not os.path.exists(os.path.join(SHOTS,s+'.png')):
        print('FAIL  截图缺失 '+s); fails+=1
print('=='*12)
print('FAILED:', fails)
EOF
