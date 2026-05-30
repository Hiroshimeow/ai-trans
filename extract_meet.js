const fs = require('fs');
const workspaceText = fs.readFileSync('app/src/main/java/com/example/ui/WorkspaceView.kt', 'utf-8');
const workspaceLines = workspaceText.split(/\r?\n/);

const startIdxLine = workspaceLines.findIndex(l => l.includes('fun MeetingTabContent('));
let startIdx = startIdxLine;
while (!workspaceLines[startIdx].includes('@Composable')) {
    startIdx--;
}

const settingsLines = workspaceLines.slice(startIdx);
const imports = fs.readFileSync('imports.txt', 'utf-8');

fs.writeFileSync('app/src/main/java/com/example/ui/MeetingTabContent.kt', 'package com.example.ui\n' + imports + '\n' + settingsLines.join('\n') + '\n');

workspaceLines.splice(startIdx, workspaceLines.length - startIdx);
fs.writeFileSync('app/src/main/java/com/example/ui/WorkspaceView.kt', workspaceLines.join('\n') + '\n');
console.log('done extraction meeting');
