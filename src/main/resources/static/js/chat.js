// ===== State =====
let currentConversationId = null;
let isStreaming = false;

// ===== Initialization =====
document.addEventListener('DOMContentLoaded', function() {
    loadConversations();
    setupEnterKey();
    setupDragDrop();
});

// ===== Enter key handling =====
function setupEnterKey() {
    const input = document.getElementById('messageInput');
    if (!input) return;
    input.addEventListener('keydown', function(e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });
}

// ===== Drag & Drop =====
function setupDragDrop() {
    const dropZone = document.getElementById('messagesContainer');
    if (!dropZone) return;

    let dragCounter = 0;

    dropZone.addEventListener('dragenter', function(e) {
        e.preventDefault();
        e.stopPropagation();
        dragCounter++;
        dropZone.classList.add('drag-over');
    });

    dropZone.addEventListener('dragover', function(e) {
        e.preventDefault();
        e.stopPropagation();
    });

    dropZone.addEventListener('dragleave', function(e) {
        e.preventDefault();
        e.stopPropagation();
        dragCounter--;
        if (dragCounter === 0) dropZone.classList.remove('drag-over');
    });

    dropZone.addEventListener('drop', function(e) {
        e.preventDefault();
        e.stopPropagation();
        dragCounter = 0;
        dropZone.classList.remove('drag-over');

        const files = e.dataTransfer.files;
        if (files.length > 0) {
            handleDroppedFile(files[0]);
        }
    });
}

function handleDroppedFile(file) {
    const input = document.getElementById('fileInput');
    if (!input) return;
    // Set the file on the hidden input and trigger upload
    const dt = new DataTransfer();
    dt.items.add(file);
    input.files = dt.files;
    uploadFile(input);
}

// ===== Auto Resize Textarea =====
function autoResize(textarea) {
    textarea.style.height = 'auto';
    textarea.style.height = Math.min(textarea.scrollHeight, 200) + 'px';
}

// ===== Load Conversations =====
async function loadConversations() {
    const list = document.getElementById('conversationList');
    if (!list) return;

    try {
        const response = await fetch('/api/conversations');
        const result = await response.json();

        if (result.code === 200 && result.data) {
            if (result.data.length === 0) {
                list.innerHTML = '<div class="loading-spinner" style="padding: 1rem; font-size: 0.85rem;">暂无对话，开始新对话吧</div>';
                return;
            }

            list.innerHTML = result.data.map(conv => `
                <div class="conversation-item ${conv.id === currentConversationId ? 'active' : ''}"
                     onclick="switchConversation(${conv.id})">
                    <span class="conv-icon">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                        </svg>
                    </span>
                    <span class="conv-title" title="${escapeHtml(conv.title)}">${escapeHtml(conv.title)}</span>
                    <button class="conv-delete" onclick="event.stopPropagation(); deleteConversation(${conv.id})"
                            title="删除对话">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                        </svg>
                    </button>
                </div>
            `).join('');
        }
    } catch (err) {
        console.error('加载对话列表失败:', err);
    }
}

// ===== Switch Conversation =====
async function switchConversation(conversationId) {
    if (isStreaming) return;
    if (conversationId === currentConversationId) return;

    currentConversationId = conversationId;
    const welcomeScreen = document.getElementById('welcomeScreen');
    const messageList = document.getElementById('messageList');

    if (welcomeScreen) welcomeScreen.style.display = 'none';
    if (messageList) messageList.innerHTML = '<div class="loading-spinner">加载中...</div>';

    loadConversations();

    try {
        const response = await fetch(`/api/conversations/${conversationId}`);
        const result = await response.json();

        if (result.code === 200 && result.data) {
            renderMessages(result.data.messages || []);
        }
    } catch (err) {
        console.error('加载对话失败:', err);
        if (messageList) messageList.innerHTML = '<div class="loading-spinner">加载失败</div>';
    }
}

// ===== Render Messages =====
function renderMessages(messages) {
    const messageList = document.getElementById('messageList');
    const welcomeScreen = document.getElementById('welcomeScreen');
    if (!messageList) return;
    if (welcomeScreen) welcomeScreen.style.display = 'none';
    if (!messages || messages.length === 0) { messageList.innerHTML = ''; return; }
    messageList.innerHTML = messages.map(msg => createMessageHTML(msg.role, msg.content)).join('');
    scrollToBottom();
}

function createMessageHTML(role, content) {
    const avatar = role === 'user' ? 'U' : 'AI';
    return `
        <div class="message ${role}">
            <div class="message-avatar">${avatar}</div>
            <div class="message-bubble">${formatContent(content)}</div>
        </div>
    `;
}

// ===== Format Content (with file links) =====
function formatContent(content) {
    if (!content) return '';

    let html = escapeHtml(content);

    // File download links: [filename.docx](download_url)
    html = html.replace(/\[([^\]]+\.\w+)\]\(([^)]+)\)/g,
        '<a href="$2" class="file-link" target="_blank">📎 $1</a>');

    // Image preview: ![filename](url)
    html = html.replace(/!\[([^\]]*)\]\(([^)]+)\)/g,
        '<div class="file-preview"><img src="$2" alt="$1" loading="lazy"></div>');

    // Code blocks
    html = html.replace(/```(\w*)\n?([\s\S]*?)```/g, function(match, lang, code) {
        return '<pre><code>' + escapeHtml(code.trim()) + '</code></pre>';
    });

    // Inline code
    html = html.replace(/`([^`\n]+)`/g, '<code>$1</code>');

    // Bold
    html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');

    // Italic
    html = html.replace(/\*([^*]+)\*/g, '<em>$1</em>');

    // Line breaks
    html = html.replace(/\n/g, '<br>');

    return html;
}

// ===== Create New Conversation =====
function createNewConversation() {
    if (isStreaming) return;
    currentConversationId = null;
    const welcomeScreen = document.getElementById('welcomeScreen');
    const messageList = document.getElementById('messageList');
    if (welcomeScreen) welcomeScreen.style.display = 'flex';
    if (messageList) messageList.innerHTML = '';
    loadConversations();
    const input = document.getElementById('messageInput');
    if (input) { input.focus(); input.value = ''; autoResize(input); }
}

// ===== Suggest Document Generation =====
function suggestGenerate(type) {
    const typeNames = {word:'Word', ppt:'PPT', pdf:'PDF', txt:'TXT'};
    const name = typeNames[type] || type.toUpperCase();
    const input = document.getElementById('messageInput');
    if (input) {
        input.value = `帮我生成一个${name}文档，主题是...`;
        input.focus();
        autoResize(input);
    }
}

// ===== File Upload =====
async function uploadFile(input) {
    const file = input.files[0];
    if (!file) return;

    const messageList = document.getElementById('messageList');
    const welcomeScreen = document.getElementById('welcomeScreen');
    if (welcomeScreen) welcomeScreen.style.display = 'none';

    // Show upload progress
    messageList.insertAdjacentHTML('beforeend',
        `<div class="message user">
            <div class="message-avatar">U</div>
            <div class="message-bubble">上传文件中: ${escapeHtml(file.name)}...</div>
        </div>`
    );
    scrollToBottom();

    const formData = new FormData();
    formData.append('file', file);

    try {
        const response = await fetch('/api/files/upload', {
            method: 'POST',
            body: formData
        });
        const result = await response.json();

        if (result.code === 200 && result.data) {
            const fileInfo = result.data;

            // Update the user message with file info
            const lastMsg = messageList.lastElementChild;
            if (lastMsg) {
                let previewHtml = `已上传: <a href="${fileInfo.fileUrl}" class="file-link" target="_blank">📎 ${escapeHtml(fileInfo.fileName)}</a>`;
                if (file.type.startsWith('image/')) {
                    previewHtml += `<div class="file-preview"><img src="${fileInfo.fileUrl}" alt="${escapeHtml(fileInfo.fileName)}" loading="lazy"></div>`;
                }
                lastMsg.querySelector('.message-bubble').innerHTML = previewHtml;
            }

            // If it's an image, auto-send for analysis
            if (file.type.startsWith('image/')) {
                await analyzeImage(fileInfo);
            } else {
                showFileWithConversion(messageList, fileInfo);
                await sendFileMessage(`已上传文件: ${fileInfo.fileName}\n文件路径: ${fileInfo.fileUrl}\n请分析这个文件的内容。`, fileInfo.fileUrl, fileInfo.fileName);
            }
        } else {
            showErrorMessage(messageList, '上传失败: ' + (result.message || '未知错误'));
        }
    } catch (err) {
        console.error('上传失败:', err);
        showErrorMessage(messageList, '上传失败: ' + err.message);
    }

    input.value = '';
}

// ===== Analyze Image then Send =====
async function analyzeImage(fileInfo) {
    const messageList = document.getElementById('messageList');

    try {
        const resp = await fetch('/api/files/analyze-image', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                filePath: fileInfo.fileUrl,
                fileName: fileInfo.fileName
            })
        });
        const result = await resp.json();

        if (result.code === 200 && result.data) {
            const content = '请分析这张图片: ' + fileInfo.fileUrl + '\n\n图片信息:\n' + result.data.content;
            await sendFileMessage(content, fileInfo.fileUrl, fileInfo.fileName);
        }
    } catch (err) {
        console.error('分析失败:', err);
        await sendFileMessage('请分析这张图片', fileInfo.fileUrl, fileInfo.fileName);
    }
}

// ===== Send Message with File Context =====
async function sendFileMessage(text, fileUrl, fileName) {
    const input = document.getElementById('messageInput');
    input.value = text;
    autoResize(input);
    await sendMessage();
}

// ===== Show File with Convert Options =====
function showFileWithConversion(messageList, fileInfo) {
    const lastMsg = messageList.lastElementChild;
    if (!lastMsg) return;

    const ext = fileInfo.fileName.split('.').pop().toLowerCase();
    const docTypes = ['docx', 'pdf', 'pptx', 'txt', 'md'];
    if (!docTypes.includes(ext)) return;

    const convertTo = docTypes.filter(t => t !== ext);
    let html = `已上传: <a href="${fileInfo.fileUrl}" class="file-link" target="_blank">📎 ${escapeHtml(fileInfo.fileName)}</a>`;
    html += `<div style="display:flex;gap:4px;margin-top:6px;flex-wrap:wrap;">`;
    html += `<span style="font-size:0.75rem;color:var(--text-secondary);padding-right:4px;">转换:</span>`;
    for (const target of convertTo) {
        html += `<button class="convert-btn" onclick="convertFile('${fileInfo.fileUrl}','${escapeHtml(fileInfo.fileName)}','${target}')" style="font-size:0.7rem;padding:2px 8px;border:1px solid var(--border);border-radius:8px;cursor:pointer;">.${target}</button>`;
    }
    html += `</div>`;

    lastMsg.querySelector('.message-bubble').innerHTML = html;
}

// ===== Convert File =====
async function convertFile(fileUrl, fileName, targetType) {
    const messageList = document.getElementById('messageList');
    const convId = Date.now();

    messageList.insertAdjacentHTML('beforeend',
        `<div class="message assistant" id="conv-${convId}">
            <div class="message-avatar">AI</div>
            <div class="message-bubble">🔄 正在转换 ${escapeHtml(fileName)} → .${targetType} ...</div>
        </div>`
    );
    scrollToBottom();

    try {
        const response = await fetch('/api/files/convert', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ filePath: fileUrl, fileName: fileName, targetType: targetType })
        });
        const result = await response.json();

        const el = document.getElementById(`conv-${convId}`);
        if (result.code === 200 && result.data) {
            if (el) {
                el.querySelector('.message-bubble').innerHTML =
                    `✅ 转换完成！<br><br><a href="${result.data.fileUrl}" class="file-link" target="_blank">📎 下载 ${escapeHtml(result.data.fileName)}</a>`;
            }
        } else {
            if (el) el.querySelector('.message-bubble').innerHTML = '❌ 转换失败: ' + escapeHtml(result.message || '未知错误');
        }
    } catch (err) {
        const el = document.getElementById(`conv-${convId}`);
        if (el) el.querySelector('.message-bubble').innerHTML = '❌ 转换失败: ' + escapeHtml(err.message);
    }
    scrollToBottom();
}

// ===== Send Message =====
async function sendMessage() {
    const input = document.getElementById('messageInput');
    const sendBtn = document.getElementById('sendButton');
    if (!input || isStreaming) return;

    const message = input.value.trim();
    if (!message) return;

    // Check for document generation commands
    const docMatch = message.match(/生成(?:一个)?(word|ppt|pptx|pdf|txt|md|markdown|文档|文件)(?:文档)?[，,。]?\s*(.*)/i);
    if (docMatch) {
        let type = docMatch[1].toLowerCase();
        let topic = docMatch[2].trim();
        if (type === 'pptx') type = 'ppt';
        if (type === 'markdown') type = 'md';
        if (type === '文档' || type === '文件') type = 'docx';
        if (!topic) topic = '未命名文档';
        await generateDocument(type, topic, message);
        return;
    }

    input.value = '';
    autoResize(input);

    const welcomeScreen = document.getElementById('welcomeScreen');
    if (welcomeScreen) welcomeScreen.style.display = 'none';

    const messageList = document.getElementById('messageList');

    // Show user message
    messageList.insertAdjacentHTML('beforeend',
        `<div class="message user">
            <div class="message-avatar">U</div>
            <div class="message-bubble">${formatContent(message)}</div>
        </div>`
    );

    // Show typing indicator
    const typingId = 'typing-' + Date.now();
    messageList.insertAdjacentHTML('beforeend',
        `<div class="typing-indicator" id="${typingId}">
            <div class="message-avatar">AI</div>
            <div class="typing-dots"><span></span><span></span><span></span></div>
        </div>`
    );
    scrollToBottom();

    isStreaming = true;
    sendBtn.disabled = true;

    try {
        await streamWithReadableStream(message, messageList, typingId);
    } catch (err) {
        console.warn('SSE failed, trying non-streaming:', err);
        try {
            await fallbackNonStreaming(message, messageList, typingId);
        } catch (fallbackErr) {
            removeTypingIndicator(typingId);
            showErrorMessage(messageList, fallbackErr.message || '消息发送失败');
        }
    }

    isStreaming = false;
    sendBtn.disabled = false;
    scrollToBottom();
}

// ===== Generate Document =====
async function generateDocument(type, topic, userMessage) {
    const messageList = document.getElementById('messageList');
    const welcomeScreen = document.getElementById('welcomeScreen');
    if (welcomeScreen) welcomeScreen.style.display = 'none';

    const typeNames = {docx:'Word', ppt:'PPT', pdf:'PDF', txt:'TXT', md:'Markdown'};
    const typeName = typeNames[type] || type.toUpperCase();

    // Show user message
    messageList.insertAdjacentHTML('beforeend',
        `<div class="message user">
            <div class="message-avatar">U</div>
            <div class="message-bubble">📝 生成${typeName}文档: ${escapeHtml(topic)}</div>
        </div>`
    );

    // Show generating indicator
    const genId = 'gen-' + Date.now();
    messageList.insertAdjacentHTML('beforeend',
        `<div class="message assistant">
            <div class="message-avatar">AI</div>
            <div class="message-bubble" id="${genId}">⏳ 正在生成 ${typeName} 文档...</div>
        </div>`
    );
    scrollToBottom();

    try {
        // First ask AI to generate content
        const aiResponse = await fetch('/api/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                message: `请为"${topic}"生成详细的${typeName}文档内容。要求内容充实、结构清晰，包含完整的章节。直接输出文档内容，不要解释。`
            })
        });
        const aiResult = await aiResponse.json();

        if (aiResult.code !== 200 || !aiResult.data) {
            throw new Error('AI生成内容失败');
        }

        const content = aiResult.data.content || '';

        // Now generate the actual document file
        const docResponse = await fetch('/api/files/generate-doc', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                type: type,
                title: topic,
                content: content
            })
        });
        const docResult = await docResponse.json();

        if (docResult.code === 200 && docResult.data) {
            const genEl = document.getElementById(genId);
            if (genEl) {
                const fileUrl = docResult.data.fileUrl;
                const fileName = docResult.data.fileName;
                genEl.innerHTML = `✅ ${typeName} 文档已生成！<br><br>`
                    + `<a href="${fileUrl}" class="file-link" target="_blank">📎 下载 ${escapeHtml(fileName)}</a>`;
                // Also send to streaming to save in conversation
                try {
                    await fetch('/api/chat/stream', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            message: userMessage + `\n\n[已生成${typeName}: ${fileName}](${fileUrl})`
                        })
                    });
                } catch(e) {}
                loadConversations();
            }
        } else {
            throw new Error(docResult.message || '文档生成失败');
        }
    } catch (err) {
        console.error('文档生成失败:', err);
        const genEl = document.getElementById(genId);
        if (genEl) genEl.innerHTML = '❌ 文档生成失败: ' + escapeHtml(err.message);
    }
    scrollToBottom();
}

// ===== SSE Streaming =====
async function streamWithReadableStream(message, messageList, typingId) {
    const response = await fetch('/api/chat/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ conversationId: currentConversationId, message: message })
    });

    if (!response.ok) throw new Error('请求失败: ' + response.status);
    if (!response.body || !response.body.getReader) throw new Error('浏览器不支持流式读取');

    removeTypingIndicator(typingId);

    const msgDiv = document.createElement('div');
    msgDiv.className = 'message assistant';
    msgDiv.innerHTML = `<div class="message-avatar">AI</div><div class="message-bubble" id="streaming-content"></div>`;
    messageList.appendChild(msgDiv);

    const contentDiv = document.getElementById('streaming-content');
    let fullContent = '';

    const reader = response.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';
    let isDone = false;
    let timeout = setTimeout(() => { if (!isDone) { isDone = true; reader.cancel(); } }, 60000);

    try {
        while (!isDone) {
            const { done, value } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });
            let idx;
            while ((idx = buffer.indexOf('\n\n')) !== -1) {
                const eventBlock = buffer.substring(0, idx);
                buffer = buffer.substring(idx + 2);
                const lines = eventBlock.split('\n');
                let eventType = '', data = '';

                for (const line of lines) {
                    if (line.startsWith('event: ')) eventType = line.substring(7).trim();
                    else if (line.startsWith('data: ')) data = line.substring(6).trim();
                }

                if (!eventType) continue;

                if (eventType === 'meta' && data) {
                    try { const meta = JSON.parse(data); if (meta.conversationId) currentConversationId = meta.conversationId; } catch(e) {}
                } else if (eventType === 'message' && data) {
                    fullContent += data;
                    if (contentDiv) { contentDiv.innerHTML = formatContent(fullContent); scrollToBottom(); }
                } else if (eventType === 'title' && data) {
                    loadConversations();
                } else if (eventType === 'notice' && data) {
                    const noticeEl = document.createElement('div');
                    noticeEl.style.cssText = 'text-align:center;font-size:0.8rem;color:var(--text-secondary);padding:4px 0;';
                    noticeEl.textContent = data;
                    messageList.appendChild(noticeEl);
                    scrollToBottom();
                } else if (eventType === 'error' && data) {
                    fullContent += '\n\n[错误: ' + data + ']';
                    if (contentDiv) contentDiv.innerHTML = formatContent(fullContent);
                    isDone = true;
                } else if (eventType === 'done') {
                    isDone = true;
                    break;
                }
            }
        }
    } finally { clearTimeout(timeout); }

    if (contentDiv) contentDiv.innerHTML = formatContent(fullContent);
    if (fullContent.length > 0) loadConversations();
    else throw new Error('AI 回复为空');
}

// ===== Fallback Non-Streaming =====
async function fallbackNonStreaming(message, messageList, typingId) {
    removeTypingIndicator(typingId);
    const msgDiv = document.createElement('div');
    msgDiv.className = 'message assistant';
    msgDiv.innerHTML = `<div class="message-avatar">AI</div><div class="message-bubble" id="streaming-content"></div>`;
    messageList.appendChild(msgDiv);
    const contentDiv = document.getElementById('streaming-content');
    if (contentDiv) contentDiv.textContent = '思考中...';

    const response = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ conversationId: currentConversationId, message: message })
    });
    const result = await response.json();

    if (result.code === 200 && result.data) {
        if (result.data.conversationId) currentConversationId = result.data.conversationId;
        if (result.data.content && contentDiv) {
            contentDiv.innerHTML = formatContent(result.data.content);
            scrollToBottom();
        }
        loadConversations();
    } else {
        throw new Error((result.data && result.data.content) || '请求失败');
    }
}

// ===== Delete Conversation =====
async function deleteConversation(conversationId) {
    if (!confirm('确定要删除这个对话吗？')) return;
    try {
        const response = await fetch(`/api/conversations/${conversationId}`, { method: 'DELETE' });
        const result = await response.json();
        if (result.code === 200) {
            if (currentConversationId === conversationId) createNewConversation();
            loadConversations();
        }
    } catch (err) { console.error('删除失败:', err); }
}

// ===== Logout =====
async function logout() {
    try { await fetch('/api/auth/logout', { method: 'POST' }); window.location.href = '/login'; }
    catch (err) { window.location.href = '/login'; }
}

// ===== Utility =====
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function scrollToBottom() {
    const container = document.getElementById('messagesContainer');
    if (container) container.scrollTop = container.scrollHeight;
}

function removeTypingIndicator(typingId) { const el = document.getElementById(typingId); if (el) el.remove(); }

function showErrorMessage(messageList, msg) {
    messageList.insertAdjacentHTML('beforeend',
        `<div class="message assistant"><div class="message-avatar">AI</div><div class="message-bubble" style="color: var(--error);">抱歉，${escapeHtml(msg)}</div></div>`);
}

// ===== Mobile Sidebar =====
function toggleSidebar() {
    document.getElementById('sidebar').classList.toggle('open');
    document.getElementById('sidebarOverlay').classList.toggle('open');
}

function isMobile() { return window.innerWidth <= 768; }

// ===== Daily Utility Functions =====
function quickCalc() {
    const input = document.getElementById('messageInput');
    if (!input) return;
    input.value = '帮我计算: ';
    input.focus();
    input.selectionStart = input.selectionEnd = input.value.length;
    autoResize(input);
}

function quickDate() {
    const now = new Date();
    const days = ['日', '一', '二', '三', '四', '五', '六'];
    const day = days[now.getDay()];
    const opts = { year: 'numeric', month: 'long', day: 'numeric' };
    const dateStr = now.toLocaleDateString('zh-CN', opts);
    const timeStr = now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
    const input = document.getElementById('messageInput');
    if (!input) return;
    input.value = `当前时间: ${dateStr} 星期${day} ${timeStr}`;
    input.focus();
    autoResize(input);
}

function quickConvert() {
    const input = document.getElementById('messageInput');
    if (!input) return;
    input.value = '帮我换算: ';
    input.focus();
    input.selectionStart = input.selectionEnd = input.value.length;
    autoResize(input);
}

function quickNote() {
    const ws = document.getElementById('welcomeScreen');
    if (ws) ws.style.display = 'none';
    const ml = document.getElementById('messageList');
    if (!ml) return;
    ml.insertAdjacentHTML('beforeend',
        `<div class="message assistant"><div class="message-avatar">AI</div><div class="message-bubble">📝 **速记模式**\n\n输入你想记录的内容，输入"整理笔记"我会帮你汇总。\n\n例如：会议记录、待办事项、灵感...\n\n输入后按发送即可。</div></div>`);
    scrollToBottom();
    const input = document.getElementById('messageInput');
    if (input) { input.focus(); input.placeholder = '输入速记内容...'; }
}
