#!/usr/bin/env node
/**
 * Desmond PTT Webhook Server
 * 
 * Receives audio from the PTT Android app, transcribes with Whisper,
 * and optionally forwards transcription to Telegram.
 * 
 * Configuration via environment variables:
 *   TELEGRAM_BOT_TOKEN  - Bot token for sending confirmation messages
 *   TELEGRAM_CHAT_ID    - Chat ID for confirmation messages
 *   WHISPER_MODEL       - Whisper model to use (default: "small")
 *   PORT                - Server port (default: 3457)
 * 
 * Usage: node ptt-webhook.js
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const PORT = process.env.PORT || 3457;
const UPLOAD_DIR = '/tmp/ptt-uploads';
const WHISPER_MODEL = process.env.WHISPER_MODEL || 'small';
const BOT_TOKEN = process.env.TELEGRAM_BOT_TOKEN || '';
const CHAT_ID = process.env.TELEGRAM_CHAT_ID || '';

// Simple multipart parser
function parseMultipart(buffer, boundary) {
    const parts = [];
    const boundaryBuffer = Buffer.from('--' + boundary);
    let start = buffer.indexOf(boundaryBuffer);
    
    while (start !== -1) {
        start += boundaryBuffer.length;
        const end = buffer.indexOf(boundaryBuffer, start);
        if (end === -1) break;
        
        const part = buffer.slice(start, end);
        const headerEnd = part.indexOf('\r\n\r\n');
        if (headerEnd !== -1) {
            const content = part.slice(headerEnd + 4);
            const cleanContent = content.slice(0, content.length - 2);
            if (cleanContent.length > 0) {
                parts.push(cleanContent);
            }
        }
        start = end;
    }
    return parts;
}

if (!fs.existsSync(UPLOAD_DIR)) {
    fs.mkdirSync(UPLOAD_DIR, { recursive: true });
}

const server = http.createServer(async (req, res) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

    if (req.method === 'OPTIONS') {
        res.writeHead(200);
        res.end();
        return;
    }

    if (req.method === 'GET' && req.url === '/health') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ status: 'ok', service: 'desmond-ptt-webhook' }));
        return;
    }

    if (req.method === 'POST' && (req.url === '/ptt' || req.url === '/voice' || req.url === '/')) {
        try {
            const chunks = [];
            req.on('data', chunk => chunks.push(chunk));
            
            req.on('end', async () => {
                let buffer = Buffer.concat(chunks);
                const timestamp = Date.now();
                
                const contentType = req.headers['content-type'] || '';
                if (contentType.includes('multipart/form-data')) {
                    const boundary = contentType.split('boundary=')[1];
                    if (boundary) {
                        const parts = parseMultipart(buffer, boundary);
                        if (parts.length > 0) {
                            buffer = parts[0];
                            console.log(`[${new Date().toISOString()}] Extracted ${buffer.length} bytes from multipart`);
                        }
                    }
                }
                
                const audioPath = path.join(UPLOAD_DIR, `ptt_${timestamp}.m4a`);
                console.log(`[${new Date().toISOString()}] Received PTT audio: ${buffer.length} bytes`);
                
                fs.writeFileSync(audioPath, buffer);
                
                // Transcribe with Whisper
                let transcription = '';
                try {
                    console.log(`[${new Date().toISOString()}] Transcribing with Whisper (${WHISPER_MODEL})...`);
                    
                    execSync(
                        `whisper "${audioPath}" --model ${WHISPER_MODEL} --language en --output_format txt --output_dir /tmp 2>/dev/null`,
                        { encoding: 'utf8', timeout: 120000 }
                    );
                    
                    const txtPath = `/tmp/ptt_${timestamp}.txt`;
                    if (fs.existsSync(txtPath)) {
                        transcription = fs.readFileSync(txtPath, 'utf8').trim();
                        fs.unlinkSync(txtPath);
                    }
                    
                    console.log(`[${new Date().toISOString()}] Transcription: "${transcription}"`);
                } catch (whisperError) {
                    console.error(`[${new Date().toISOString()}] Whisper error:`, whisperError.message);
                    
                    // Fallback: convert to wav first
                    try {
                        const wavPath = audioPath.replace('.m4a', '.wav');
                        execSync(`ffmpeg -i "${audioPath}" -ar 16000 -ac 1 "${wavPath}" -y 2>/dev/null`);
                        
                        execSync(
                            `whisper "${wavPath}" --model ${WHISPER_MODEL} --language en --output_format txt --output_dir /tmp 2>/dev/null`,
                            { encoding: 'utf8', timeout: 120000 }
                        );
                        
                        const txtPath = `/tmp/ptt_${timestamp}.txt`;
                        if (fs.existsSync(txtPath)) {
                            transcription = fs.readFileSync(txtPath, 'utf8').trim();
                            fs.unlinkSync(txtPath);
                        }
                        fs.unlinkSync(wavPath);
                    } catch (fallbackError) {
                        console.error(`[${new Date().toISOString()}] Fallback also failed:`, fallbackError.message);
                    }
                }
                
                if (transcription) {
                    console.log(`[${new Date().toISOString()}] ✅ SUCCESS: "${transcription}"`);
                    
                    // Notify via Telegram if configured
                    if (BOT_TOKEN && CHAT_ID) {
                        try {
                            execSync(
                                `curl -s -X POST "https://api.telegram.org/bot${BOT_TOKEN}/sendMessage" ` +
                                `-d "chat_id=${CHAT_ID}" -d "text=🎤 Heard: ${encodeURIComponent(transcription)}"`,
                                { encoding: 'utf8' }
                            );
                        } catch (e) {
                            console.error(`[${new Date().toISOString()}] Telegram notify error:`, e.message);
                        }
                    }
                    
                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ success: true, transcription }));
                } else {
                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ success: false, error: 'Transcription failed' }));
                }
            });
        } catch (error) {
            console.error(`[${new Date().toISOString()}] Error:`, error);
            res.writeHead(500, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: false, error: error.message }));
        }
        return;
    }

    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Not found' }));
});

server.listen(PORT, '0.0.0.0', () => {
    console.log(`[${new Date().toISOString()}] Desmond PTT Webhook listening on port ${PORT}`);
    console.log(`  POST /ptt or /voice  - Upload audio for transcription`);
    console.log(`  GET  /health         - Health check`);
    if (!BOT_TOKEN) console.log('  ⚠️  TELEGRAM_BOT_TOKEN not set — Telegram notifications disabled');
});
