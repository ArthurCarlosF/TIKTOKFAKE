const SHEET_NAME = 'videos';
const ADMIN_TOKEN_PROPERTY = 'ADMIN_TOKEN';

function doGet(e) {
  const action = String(e.parameter.action || 'feed');

  if (action === 'feed') {
    return jsonResponse(buildFeed());
  }

  return jsonResponse({ error: 'Unknown action' }, 400);
}

function doPost(e) {
  const body = parseBody(e);
  const action = String(body.action || '');

  if (!isAuthorized(body.token)) {
    return jsonResponse({ error: 'Unauthorized' }, 401);
  }

  if (action === 'upsertVideo') {
    return jsonResponse(upsertVideo(body.video || {}));
  }

  if (action === 'deleteVideo') {
    return jsonResponse(deleteVideo(String(body.id || '')));
  }

  return jsonResponse({ error: 'Unknown action' }, 400);
}

function buildFeed() {
  const sheet = getSheet();
  const rows = sheet.getDataRange().getValues();
  const headers = rows.shift().map(String);
  const now = new Date().toISOString();

  const videos = rows
    .map(row => rowToObject(headers, row))
    .filter(video => normalizeBoolean(video.active))
    .map(normalizeVideo)
    .sort((a, b) => {
      const orderDiff = Number(a.order || 0) - Number(b.order || 0);
      if (orderDiff !== 0) return orderDiff;
      return String(b.updatedAt || '').localeCompare(String(a.updatedAt || ''));
    });

  return {
    version: 1,
    updatedAt: now,
    videos
  };
}

function upsertVideo(video) {
  const sheet = getSheet();
  ensureHeaders(sheet);
  const rows = sheet.getDataRange().getValues();
  const headers = rows[0].map(String);
  const id = String(video.id || '').trim();

  if (!id) {
    throw new Error('Missing video.id');
  }

  const normalized = normalizeVideo({
    id,
    title: video.title,
    videoUrl: video.videoUrl,
    thumbnailUrl: video.thumbnailUrl,
    active: video.active,
    category: video.category,
    sourceLabel: video.sourceLabel,
    caregiverNote: video.caregiverNote,
    order: video.order,
    updatedAt: new Date().toISOString()
  });

  const rowIndex = findRowIndexById(rows, id);
  const values = headers.map(header => normalized[header] ?? '');

  if (rowIndex >= 0) {
    sheet.getRange(rowIndex + 1, 1, 1, headers.length).setValues([values]);
  } else {
    sheet.appendRow(values);
  }

  return { ok: true, video: normalized };
}

function deleteVideo(id) {
  const sheet = getSheet();
  const rows = sheet.getDataRange().getValues();
  const rowIndex = findRowIndexById(rows, id);

  if (rowIndex < 1) {
    return { ok: true, deleted: false };
  }

  sheet.deleteRow(rowIndex + 1);
  return { ok: true, deleted: true };
}

function getSheet() {
  const spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = spreadsheet.getSheetByName(SHEET_NAME);

  if (!sheet) {
    sheet = spreadsheet.insertSheet(SHEET_NAME);
  }

  ensureHeaders(sheet);
  return sheet;
}

function ensureHeaders(sheet) {
  const headers = [
    'id',
    'title',
    'videoUrl',
    'thumbnailUrl',
    'active',
    'category',
    'sourceLabel',
    'caregiverNote',
    'order',
    'updatedAt'
  ];

  const firstRow = sheet.getRange(1, 1, 1, headers.length).getValues()[0];
  const hasHeaders = firstRow.some(value => String(value || '').trim());

  if (!hasHeaders) {
    sheet.getRange(1, 1, 1, headers.length).setValues([headers]);
  }
}

function rowToObject(headers, row) {
  return headers.reduce((object, header, index) => {
    object[header] = row[index];
    return object;
  }, {});
}

function normalizeVideo(video) {
  return {
    id: String(video.id || '').trim(),
    title: String(video.title || '').trim(),
    videoUrl: String(video.videoUrl || '').trim(),
    thumbnailUrl: String(video.thumbnailUrl || '').trim(),
    active: normalizeBoolean(video.active),
    category: String(video.category || '').trim(),
    sourceLabel: String(video.sourceLabel || '').trim(),
    caregiverNote: String(video.caregiverNote || '').trim(),
    order: Number(video.order || 0),
    updatedAt: String(video.updatedAt || '').trim()
  };
}

function normalizeBoolean(value) {
  const text = String(value).trim().toLowerCase();
  return value === true || text === 'true' || text === '1' || text === 'yes' || text === 'sim';
}

function findRowIndexById(rows, id) {
  const needle = String(id || '').trim();
  for (let index = 1; index < rows.length; index += 1) {
    if (String(rows[index][0] || '').trim() === needle) {
      return index;
    }
  }
  return -1;
}

function parseBody(e) {
  if (!e.postData || !e.postData.contents) {
    return {};
  }
  return JSON.parse(e.postData.contents);
}

function isAuthorized(token) {
  const expected = PropertiesService.getScriptProperties().getProperty(ADMIN_TOKEN_PROPERTY);
  return Boolean(expected) && String(token || '') === expected;
}

function jsonResponse(payload, statusCode) {
  const output = ContentService
    .createTextOutput(JSON.stringify(payload))
    .setMimeType(ContentService.MimeType.JSON);

  if (statusCode) {
    // Apps Script ContentService does not reliably expose custom HTTP statuses
    // for all deployment modes, so clients should also inspect the JSON body.
  }

  return output;
}
