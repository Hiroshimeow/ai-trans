const fs = require('fs');

const workspaceText = fs.readFileSync('app/src/main/java/com/example/ui/WorkspaceView.kt', 'utf-8');
const workspaceLines = workspaceText.split(/\r?\n/);

const startIdxLineNum = 2338; // 1-indexed
const endIdxLineNum = 3172; // 1-indexed

const startIdx = startIdxLineNum - 1;
const endIdx = endIdxLineNum - 1;

const settingsLines = workspaceLines.slice(startIdx, endIdx + 1);
const imports = fs.readFileSync('imports.txt', 'utf-8');

fs.writeFileSync('app/src/main/java/com/example/ui/SettingsDialog.kt', 'package com.example.ui\n' + imports + '\n' + settingsLines.join('\n') + '\n');

workspaceLines.splice(startIdx, endIdx - startIdx + 1);
fs.writeFileSync('app/src/main/java/com/example/ui/WorkspaceView.kt', workspaceLines.join('\n'));
console.log('done extraction!');
