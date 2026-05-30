const fs = require('fs');
const workspaceText = fs.readFileSync('app/src/main/java/com/example/ui/WorkspaceView.kt', 'utf-8');
const workspaceLines = workspaceText.split(/\r?\n/);

const startIdx = 1864; // 0-indexed index of line 1865

const settingsLines = workspaceLines.slice(startIdx);
const imports = fs.readFileSync('imports.txt', 'utf-8');

fs.writeFileSync('app/src/main/java/com/example/ui/AttachmentsTabContent.kt', 'package com.example.ui\n' + imports + '\n' + settingsLines.join('\n') + '\n');

workspaceLines.splice(startIdx, workspaceLines.length - startIdx);
fs.writeFileSync('app/src/main/java/com/example/ui/WorkspaceView.kt', workspaceLines.join('\n') + '\n');
console.log('done extraction attachments');
