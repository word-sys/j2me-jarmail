require('dotenv').config();
const crypto = require('crypto');
const express = require('express');
const compression = require('compression');
const bodyParser = require('body-parser');
const fs = require('fs');
const path = require('path');
const imaps = require('imap-simple');
const { simpleParser } = require('mailparser');
const nodemailer = require('nodemailer');

const app = express();
const PORT = 3000;


const STORAGE_PATH = './mail_vault';
const ATTACH_PATH = path.join(STORAGE_PATH, 'attachments');
const TEMP_UPLOAD_PATH = path.join(STORAGE_PATH, 'temp_uploads');
const PUBLIC_PATH = './public';
const PAIRED_DEVICES_FILE = './paired_devices.json';

if (!fs.existsSync(STORAGE_PATH)) fs.mkdirSync(STORAGE_PATH);
if (!fs.existsSync(ATTACH_PATH)) fs.mkdirSync(ATTACH_PATH, { recursive: true });
if (!fs.existsSync(TEMP_UPLOAD_PATH)) fs.mkdirSync(TEMP_UPLOAD_PATH, { recursive: true });
if (!fs.existsSync(PUBLIC_PATH)) fs.mkdirSync(PUBLIC_PATH);



const GMAIL_USER = process.env.GMAIL_USER;
const GMAIL_APP_PASSWORD = process.env.GMAIL_APP_PASSWORD;

if (!GMAIL_USER || !GMAIL_APP_PASSWORD) {
    console.error('');
    console.error('  [FATAL] Gmail credentials not found!');
    console.error('  Create a .env file in this directory with:');
    console.error('    GMAIL_USER=youraddress@gmail.com');
    console.error('    GMAIL_APP_PASSWORD=xxxx xxxx xxxx xxxx');
    console.error('');
    process.exit(1);
}



const IMAP_CONFIG = {
    imap: {
        user: GMAIL_USER,
        password: GMAIL_APP_PASSWORD,
        host: 'imap.gmail.com',
        port: 993,
        tls: true,
        authTimeout: 15000,
        connTimeout: 15000,
        tlsOptions: { rejectUnauthorized: false }
    }
};

const GMAIL_FOLDER = {
    inbox: 'INBOX',
    sent: '[Gmail]/Sent Mail',
    trash: '[Gmail]/Trash',
    spam: '[Gmail]/Spam'
};

const smtpTransport = nodemailer.createTransport({
    service: 'gmail',
    auth: { user: GMAIL_USER, pass: GMAIL_APP_PASSWORD }
});



let pairPin = null;
let pairPinExpiry = 0;
const SESSION_DURATION = 24 * 60 * 60 * 1000; // 24 
const activeSessions = new Map();

function loadPairedDevices() {
    try {
        if (fs.existsSync(PAIRED_DEVICES_FILE)) {
            return JSON.parse(fs.readFileSync(PAIRED_DEVICES_FILE, 'utf8'));
        }
    } catch (e) {
        console.error('[Auth] Error loading paired devices:', e.message);
    }
    return { devices: [] };
}

function savePairedDevices(data) {
    fs.writeFileSync(PAIRED_DEVICES_FILE, JSON.stringify(data, null, 2));
}

function generateAuthId(token, minuteStr) {
    return crypto.createHash('sha256').update(token + minuteStr).digest('hex');
}

function getMinuteStr(offsetMinutes) {
    const d = new Date(Date.now() + (offsetMinutes || 0) * 60000);
    const pad = n => String(n).padStart(2, '0');
    return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}T${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}`;
}


if (process.env.PAIR_MODE === 'true') {
    pairPin = String(Math.floor(100000 + Math.random() * 900000));
    pairPinExpiry = Date.now() + 5 * 60 * 1000;
}



let imapConn = null;
let imapLastUsed = 0;
const IMAP_IDLE_TIMEOUT = 5 * 60 * 1000;

async function withImap(fn) {
    if (imapConn && (Date.now() - imapLastUsed > IMAP_IDLE_TIMEOUT)) {
        try { imapConn.end(); } catch (e) { }
        imapConn = null;
    }

    for (let attempt = 0; attempt < 2; attempt++) {
        try {
            if (!imapConn) {
                imapConn = await imaps.connect(IMAP_CONFIG);
            }
            imapLastUsed = Date.now();
            return await fn(imapConn);
        } catch (err) {
            if (attempt === 0) {
                try { if (imapConn) imapConn.end(); } catch (e) { }
                imapConn = null;
                continue;
            }
            throw err;
        }
    }
}



const listCache = new Map();
const CACHE_TTL = 30 * 1000;

function getCachedList(key) {
    const entry = listCache.get(key);
    if (entry && (Date.now() - entry.time) < CACHE_TTL) return entry.data;
    listCache.delete(key);
    return null;
}

function setCacheList(key, data) {
    listCache.set(key, { data, time: Date.now() });
}

function invalidateCache() {
    listCache.clear();
}



const staticOptions = {
    setHeaders: (res, filePath) => {
        if (filePath.endsWith('.jad')) res.setHeader('Content-Type', 'text/vnd.sun.j2me.app-descriptor');
        else if (filePath.endsWith('.jar')) res.setHeader('Content-Type', 'application/java-archive');
    }
};

app.use(compression({ threshold: 256 }));
app.use(express.static(PUBLIC_PATH, staticOptions));
app.use(bodyParser.urlencoded({ extended: true }));

app.use((req, res, next) => {
    const semi = req.url.indexOf(';');
    if (semi > -1) req.url = req.url.substring(0, semi);
    next();
});

app.use((req, res, next) => {
    console.log(`[${new Date().toLocaleTimeString()}] ${req.method} ${req.url}`);
    next();
});

function requireAuth(req, res, next) {
    const token = req.headers['x-auth-token'] || req.query.token;
    if (!token) return res.status(401).json({ error: 'No auth token' });

    const session = activeSessions.get(token);
    if (!session || Date.now() > session.expiry) {
        activeSessions.delete(token);
        return res.status(401).json({ error: 'Session expired' });
    }
    next();
}


function decodeHeader(val) {
    if (!val) return '';
    if (Array.isArray(val)) val = val[0] || '';
    return String(val)
        .replace(/=\?([^?]+)\?(B|Q)\?([^?]*)\?=/gi, (_, charset, enc, data) => {
            try {
                if (enc.toUpperCase() === 'B') {
                    return Buffer.from(data, 'base64').toString('utf8');
                }
                return data.replace(/_/g, ' ')
                    .replace(/=([0-9A-F]{2})/gi, (m, h) => String.fromCharCode(parseInt(h, 16)));
            } catch (e) { return data; }
        })
        .replace(/\s+/g, ' ')
        .trim();
}

function extractSender(raw) {
    if (!raw) return '';
    const decoded = decodeHeader(raw);
    const nameQuoted = decoded.match(/^"([^"]+)"\s*</);
    if (nameQuoted) return nameQuoted[1].trim();
    const nameUnquoted = decoded.match(/^([^<]+)<[^>]+>/);
    if (nameUnquoted) return nameUnquoted[1].trim();
    const emailOnly = decoded.match(/<([^>]+)>/);
    if (emailOnly) return emailOnly[1];
    return decoded;
}

function formatDate(raw) {
    if (!raw) return '';
    try {
        const d = new Date(raw);
        if (isNaN(d.getTime())) return String(raw).substring(0, 25);

        const now = new Date();
        const pad = n => String(n).padStart(2, '0');
        const M = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

        const isToday = d.getDate() === now.getDate() &&
            d.getMonth() === now.getMonth() &&
            d.getFullYear() === now.getFullYear();

        if (isToday) {
            return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
        }

        const yesterday = new Date(now);
        yesterday.setDate(now.getDate() - 1);
        const isYesterday = d.getDate() === yesterday.getDate() &&
            d.getMonth() === yesterday.getMonth() &&
            d.getFullYear() === yesterday.getFullYear();

        if (isYesterday) {
            return `Yesterday ${pad(d.getHours())}:${pad(d.getMinutes())}`;
        }

        if (d.getFullYear() === now.getFullYear()) {
            return `${pad(d.getDate())} ${M[d.getMonth()]} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
        }

        return `${pad(d.getDate())} ${M[d.getMonth()]} ${d.getFullYear().toString().substring(2)}`;
    } catch (e) { return String(raw); }
}

function hasAttachmentsInStruct(struct) {
    if (!struct) return false;
    if (Array.isArray(struct)) {
        return struct.some(p => hasAttachmentsInStruct(p));
    }
    if (struct.disposition) {
        const dtype = (struct.disposition.type || '').toLowerCase();
        if (dtype === 'attachment') return true;
    }
    return false;
}

function htmlToText(html) {
    if (!html) return '';
    return html
        .replace(/<style[\s\S]*?<\/style>/gi, '')
        .replace(/<script[\s\S]*?<\/script>/gi, '')
        .replace(/<br\s*\/?>/gi, '\n')
        .replace(/<\/p>/gi, '\n\n')
        .replace(/<\/div>/gi, '\n')
        .replace(/<\/tr>/gi, '\n')
        .replace(/<\/td>/gi, ' | ')
        .replace(/<[^>]+>/g, '')
        .replace(/&amp;/g, '&')
        .replace(/&lt;/g, '<')
        .replace(/&gt;/g, '>')
        .replace(/&nbsp;/g, ' ')
        .replace(/&#39;/g, "'")
        .replace(/&quot;/g, '"')
        .replace(/\n{3,}/g, '\n\n')
        .trim();
}

function sanitizeUnicodeBMP(obj) {
    if (typeof obj === 'string') {
        return obj.replace(/[\u{10000}-\u{10FFFF}]/gu, '');
    }
    if (Array.isArray(obj)) {
        return obj.map(sanitizeUnicodeBMP);
    }
    if (obj !== null && typeof obj === 'object') {
        const newObj = {};
        for (const key of Object.keys(obj)) {
            newObj[key] = sanitizeUnicodeBMP(obj[key]);
        }
        return newObj;
    }
    return obj;
}



app.get('/', (req, res) => {
    res.sendFile(path.resolve(PUBLIC_PATH, 'login.html'));
});

app.post('/pair', (req, res) => {
    const pin = req.body.pin;

    if (!pairPin || !pin) {
        return res.status(401).json({ error: 'Pairing not active' });
    }
    if (Date.now() > pairPinExpiry) {
        pairPin = null;
        return res.status(401).json({ error: 'PIN expired' });
    }
    if (pin !== pairPin) {
        return res.status(401).json({ error: 'Invalid PIN' });
    }

    const deviceToken = crypto.randomBytes(32).toString('hex');

    const devices = loadPairedDevices();
    devices.devices.push({
        token: deviceToken,
        pairedAt: new Date().toISOString()
    });
    savePairedDevices(devices);

    // one-time use
    pairPin = null;
    pairPinExpiry = 0;

    console.log('[Auth] New device paired successfully');
    res.json({ token: deviceToken });
});

app.post('/login', (req, res) => {
    const authId = req.body.authId;
    if (!authId) return res.status(401).json({ error: 'Missing authId' });

    const devices = loadPairedDevices();
    if (devices.devices.length === 0) {
        return res.status(401).json({ error: 'No paired devices' });
    }

    const minutes = [getMinuteStr(0), getMinuteStr(-1), getMinuteStr(1)];

    let matched = false;
    for (let di = 0; di < devices.devices.length; di++) {
        const device = devices.devices[di];
        for (let mi = 0; mi < minutes.length; mi++) {
            const expected = generateAuthId(device.token, minutes[mi]);
            if (expected === authId) {
                matched = true;
                break;
            }
        }
        if (matched) break;
    }

    if (!matched) {
        return res.status(401).json({ error: 'Auth failed' });
    }

    const sessionToken = crypto.randomBytes(32).toString('hex');
    activeSessions.set(sessionToken, { expiry: Date.now() + SESSION_DURATION });

    console.log('[Auth] Device logged in, session issued');
    res.json({ session: sessionToken });
});




async function imapFetchList(box, page, limit, query) {
    const folder = GMAIL_FOLDER[box] || 'INBOX';
    return withImap(async (conn) => {
        await conn.openBox(folder);

        let criteria = ['UNDELETED'];
        if (query) {
            criteria = ['UNDELETED', ['OR', ['SUBJECT', query], ['FROM', query]]];
        }
        const fetchOpts = {
            bodies: ['HEADER.FIELDS (FROM TO SUBJECT DATE)'],
            markSeen: false,
            struct: true
        };

        const messages = await conn.search(criteria, fetchOpts);

        messages.sort((a, b) => b.attributes.uid - a.attributes.uid);

        const startIdx = (page - 1) * limit;
        const pageSlice = messages.slice(startIdx, startIdx + limit);

        return pageSlice.map(msg => {
            const headerPart = msg.parts.find(p => p.which.startsWith('HEADER'));
            const h = (headerPart && headerPart.body) ? headerPart.body : {};

            return {
                i: String(msg.attributes.uid),
                f: extractSender(h.from ? h.from[0] : ''),
                t: decodeHeader(h.to ? h.to[0] : ''),
                s: decodeHeader(h.subject ? h.subject[0] : '') || '(no subject)',
                d: formatDate(h.date ? h.date[0] : ''),
                r: (msg.attributes.flags || []).includes('\\Seen'),
                a: hasAttachmentsInStruct(msg.attributes.struct)
            };
        });
    });
}


async function imapFetchDetail(box, uid) {
    const folder = GMAIL_FOLDER[box] || 'INBOX';
    return withImap(async (conn) => {
        await conn.openBox(folder);

        const criteria = [['UID', `${uid}:${uid}`]];
        const fetchOpts = { bodies: [''], markSeen: (box === 'inbox'), struct: true };
        const messages = await conn.search(criteria, fetchOpts);

        if (!messages || messages.length === 0) return null;

        const msg = messages[0];
        const bodyPart = msg.parts.find(p => p.which === '');
        if (!bodyPart) return null;

        const parsed = await simpleParser(bodyPart.body);

        let bodyText = '';
        if (parsed.text) {
            bodyText = parsed.text;
        } else if (parsed.html) {
            bodyText = htmlToText(parsed.html);
        }

        const MAX_BODY = 3800;
        if (bodyText.length > MAX_BODY) {
            bodyText = bodyText.substring(0, MAX_BODY) + '\n\n...[message truncated]';
        }

        const attachments = [];
        if (parsed.attachments && parsed.attachments.length > 0) {
            for (const att of parsed.attachments) {
                if (!att.filename || !att.content) continue;
                const safeName = `${uid}_${att.filename.replace(/[^a-zA-Z0-9._-]/g, '_')}`;
                const attPath = path.join(ATTACH_PATH, safeName);
                if (!fs.existsSync(attPath)) {
                    fs.writeFileSync(attPath, att.content);
                }
                attachments.push({ n: att.filename, u: `/download/${safeName}` });
            }
        }

        let senderEmail = '';
        if (parsed.from && parsed.from.value && parsed.from.value[0]) {
            senderEmail = parsed.from.value[0].address || '';
        }

        return {
            i: String(uid),
            f: parsed.from ? parsed.from.text : '',
            e: senderEmail,
            t: parsed.to ? parsed.to.text : '',
            s: parsed.subject || '(no subject)',
            d: formatDate(parsed.date ? parsed.date.toString() : ''),
            b: bodyText,
            r: (msg.attributes.flags || []).includes('\\Seen'),
            at: attachments
        };
    });
}

async function imapMarkRead(box, uid) {
    const folder = GMAIL_FOLDER[box] || 'INBOX';
    return withImap(async (conn) => {
        await conn.openBox(folder);
        await conn.addFlags(String(uid), ['\\Seen']);
    });
}

async function imapMoveToTrash(box, uid) {
    const folder = GMAIL_FOLDER[box] || 'INBOX';
    return withImap(async (conn) => {
        await conn.openBox(folder);

        if (box === 'trash') {
            await new Promise((resolve, reject) => {
                conn.imap.addFlags(String(uid), ['\\Deleted'], (err) => {
                    if (err) reject(err);
                    else resolve();
                });
            });
            await new Promise((resolve, reject) => {
                conn.imap.expunge((err) => {
                    if (err) reject(err);
                    else resolve();
                });
            });
        } else {
            await new Promise((resolve, reject) => {
                conn.imap.move(String(uid), '[Gmail]/Trash', (err) => {
                    if (err) reject(err);
                    else resolve();
                });
            });
        }
    });
}



app.post('/upload', requireAuth, (req, res) => {
    const filename = (req.query.name || 'attachment').replace(/[^a-zA-Z0-9._-]/g, '_');
    const uniqueName = `temp_${Date.now()}_${filename}`;
    const savePath = path.join(TEMP_UPLOAD_PATH, uniqueName);
    const outStream = fs.createWriteStream(savePath);

    req.pipe(outStream);

    req.on('end', () => {
        console.log(`[Upload] File saved → ${uniqueName}`);
        res.send(uniqueName);
    });

    req.on('error', (err) => {
        console.error('[Upload Error]', err.message);
        res.status(500).send('ERROR: ' + err.message);
    });
});


app.get(['/inbox', '/sentbox', '/trashbox', '/spambox'], requireAuth, async (req, res) => {
    let box = 'inbox';
    if (req.path === '/sentbox') box = 'sent';
    else if (req.path === '/trashbox') box = 'trash';
    else if (req.path === '/spambox') box = 'spam';

    const query = (req.query.q || '').trim();
    const page = Math.max(1, parseInt(req.query.page, 10) || 1);
    const limit = Math.max(1, parseInt(req.query.limit, 10) || 25);

    const cacheKey = `${box}:${page}:${limit}:${query}`;
    const cached = getCachedList(cacheKey);
    if (cached) {
        return res.json(cached);
    }

    try {
        const mails = await imapFetchList(box, page, limit, query);
        const sanitized = sanitizeUnicodeBMP(mails);
        setCacheList(cacheKey, sanitized);
        res.json(sanitized);
    } catch (err) {
        console.error('[IMAP List Error]', err.message);
        res.json([]);
    }
});


app.get('/detail', requireAuth, async (req, res) => {
    const box = req.query.box;
    const uid = req.query.id;
    if (!uid) return res.status(400).json({ error: 'Missing id' });

    try {
        const mail = await imapFetchDetail(box, uid);
        if (mail) res.json(sanitizeUnicodeBMP(mail));
        else res.status(404).json({ error: 'Not found' });
    } catch (err) {
        console.error('[IMAP Detail Error]', err.message);
        res.status(500).json({ error: 'Could not fetch message' });
    }
});


app.post('/send', requireAuth, async (req, res) => {
    const { to, subject, body, attachments } = req.body;
    if (!to) return res.status(400).send('Missing recipient');

    const mailAttachments = [];
    const pathsToClean = [];

    try {
        if (attachments && attachments.trim().length > 0) {
            const files = attachments.split(',');
            for (let i = 0; i < files.length; i++) {
                const file = files[i].trim();
                if (file.length === 0) continue;
                const safePath = path.join(TEMP_UPLOAD_PATH, path.basename(file));
                if (fs.existsSync(safePath)) {
                    const originalName = file.replace(/^temp_\d+_/, '');
                    mailAttachments.push({
                        filename: originalName,
                        path: safePath
                    });
                    pathsToClean.push(safePath);
                }
            }
        }

        const info = await smtpTransport.sendMail({
            from: GMAIL_USER,
            to: to,
            subject: subject || '(no subject)',
            text: body || '',
            attachments: mailAttachments
        });
        console.log(`[SMTP] Sent → ${to} | "${subject}" | msgId: ${info.messageId}`);
        invalidateCache();
        res.send('OK');

        for (const p of pathsToClean) {
            fs.unlink(p, err => {
                if (err) console.error('[Cleanup Error]', err.message);
            });
        }
    } catch (err) {
        console.error('[SMTP Error]', err.message);
        res.status(500).send('ERROR: ' + err.message);
    }
});


app.post('/spam', requireAuth, async (req, res) => {
    const box = req.body.box;
    const uid = req.body.id;
    if (!uid) return res.status(400).send('Missing id');

    const folder = GMAIL_FOLDER[box] || 'INBOX';
    try {
        await withImap(async (conn) => {
            await conn.openBox(folder);
            await new Promise((resolve, reject) => {
                conn.imap.move(String(uid), '[Gmail]/Spam', (err) => {
                    if (err) reject(err);
                    else resolve();
                });
            });
        });
        console.log(`[IMAP] Moved UID ${uid} (${box}) → Spam`);
        invalidateCache();
        res.send('OK');
    } catch (err) {
        console.error('[IMAP Spam Error]', err.message);
        res.status(500).send('ERROR');
    }
});

app.post('/empty', requireAuth, async (req, res) => {
    const box = req.body.box;
    if (box !== 'trash' && box !== 'spam') {
        return res.status(400).send('Invalid box');
    }

    const folder = GMAIL_FOLDER[box];
    try {
        await withImap(async (conn) => {
            await conn.openBox(folder);
            const messages = await conn.search(['ALL'], { markSeen: false });
            if (messages.length > 0) {
                const uids = messages.map(msg => String(msg.attributes.uid));
                await conn.addFlags(uids, ['\\Deleted']);
                await new Promise((resolve, reject) => {
                    conn.imap.expunge((err) => {
                        if (err) reject(err);
                        else resolve();
                    });
                });
            }
        });
        console.log(`[IMAP] Expunged all messages in ${box}`);
        invalidateCache();
        res.send('OK');
    } catch (err) {
        console.error('[IMAP Empty Error]', err.message);
        res.status(500).send('ERROR');
    }
});


app.post('/delete', requireAuth, async (req, res) => {
    const box = req.body.box;
    const uid = req.body.id;
    if (!uid) return res.status(400).send('Missing id');

    try {
        await imapMoveToTrash(box, uid);
        console.log(`[IMAP] Moved UID ${uid} (${box}) → Trash`);
        invalidateCache();
        res.send('OK');
    } catch (err) {
        console.error('[IMAP Delete Error]', err.message);
        res.status(500).send('ERROR');
    }
});


app.get('/read', requireAuth, async (req, res) => {
    const uid = req.query.id;
    const box = req.query.box === 'sent' ? 'sent' : 'inbox';
    if (uid) {
        imapMarkRead(box, uid).catch(err => console.error('[IMAP Read Error]', err.message));
    }
    res.send('OK');
});


app.get('/download/:name', (req, res) => {
    const file = path.join(ATTACH_PATH, req.params.name);
    if (fs.existsSync(file)) res.download(file);
    else res.status(404).send('Attachment not found');
});



app.listen(PORT, '0.0.0.0', () => {
    console.log('');
    console.log('  ╔══════════════════════════════════════════╗');
    console.log('  ║      MAXMAIL MASTER SERVER  v0.2         ║');
    console.log('  ║      Gmail Bridge  (IMAP + SMTP)         ║');
    console.log(`  ║  Account : ${GMAIL_USER.padEnd(30)} ║`);
    console.log(`  ║  Address : http://0.0.0.0:${PORT}           ║`);
    console.log('  ╠══════════════════════════════════════════╣');

    const devices = loadPairedDevices();
    console.log(`  ║  Paired  : ${String(devices.devices.length).padEnd(30)} ║`);

    if (pairPin) {
        console.log('  ╠══════════════════════════════════════════╣');
        console.log('  ║   PAIRING MODE ACTIVE                  ║');
        console.log(`  ║  PIN    : ${pairPin.padEnd(31)} ║`);
        console.log('  ║  Expires: 5 minutes from start           ║');
        console.log('  ║  Enter this PIN in app Settings to pair  ║');
    } else {
        console.log('  ╠══════════════════════════════════════════╣');
        console.log('  ║  To pair a new device, restart with:     ║');
        console.log('  ║  PAIR_MODE=true node server.js           ║');
    }

    console.log('  ╚══════════════════════════════════════════╝');
    console.log('');
});