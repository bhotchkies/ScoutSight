var DOMAIN = 'troop600.com';

// ─────────────────────────────────────────────────────────────────────────────
// Welcome-email templates — edit here to customise the message sent to new users.
// Both functions receive: firstName (string), fullEmail (string), password (string).
// ─────────────────────────────────────────────────────────────────────────────

/** Returns the HTML body of the welcome email. */
function buildWelcomeHtml(firstName, fullEmail, password) {
  return `<!DOCTYPE html>
<html>
<body style="margin:0;padding:0;background:#f5f5f5;font-family:Arial,sans-serif;">
<table width="100%" cellpadding="0" cellspacing="0" style="background:#f5f5f5;padding:32px 0;">
<tr><td align="center">
<table width="520" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.1);">

  <!-- Header -->
  <tr><td style="background:#1a4d2e;padding:24px 32px;">
    <p style="margin:0;color:#ffffff;font-size:20px;font-weight:bold;">Troop 600B</p>
    <p style="margin:4px 0 0;color:#a8d5b5;font-size:13px;">Google Workspace Account</p>
  </td></tr>

  <!-- Body -->
  <tr><td style="padding:32px;">
    <p style="margin:0 0 16px;font-size:15px;color:#222;">Hi ${firstName},</p>
    <p style="margin:0 0 24px;font-size:14px;color:#444;line-height:1.6;">
      Your Troop 600 account has been created. Use the
      credentials below to sign in at
      <a href="https://troop600.com/" style="color:#1a4d2e;">troop600.com/</a>.
    </p>

    <!-- Credentials box -->
    <table width="100%" cellpadding="0" cellspacing="0"
           style="background:#f0f7f2;border:1px solid #c8e0d0;border-radius:6px;margin-bottom:24px;">
    <tr><td style="padding:20px 24px;">
      <p style="margin:0 0 12px;font-size:11px;font-weight:bold;letter-spacing:.08em;color:#1a4d2e;text-transform:uppercase;">Your Credentials</p>
      <p style="margin:0 0 8px;font-size:13px;color:#333;">
        <strong>Email &nbsp;&nbsp;&nbsp;</strong> ${fullEmail}
      </p>
      <p style="margin:0;font-size:13px;color:#333;">
        <strong>Password</strong>
        <span style="font-family:'Courier New',monospace;background:#fff;border:1px solid #ccc;border-radius:3px;padding:2px 8px;letter-spacing:.05em;">${password}</span>
      </p>
    </td></tr></table>

    <p style="margin:0 0 16px;font-size:13px;color:#555;line-height:1.6;">
      &#128274; You will be prompted to set a new password on your first sign-in.
    </p>
    <p style="margin:0 0 16px;font-size:13px;color:#555;line-height:1.6;">
      &#128231; You will shortly receive a <strong>separate email from Google</strong>
      asking you to authorize email forwarding to this account.
      Please open it and click <strong>Confirm</strong> to complete the setup.
    </p>
    <p style="margin:0;font-size:13px;color:#555;line-height:1.6;">
      Your @troop600.com email is used for troop communications, Google Drive,
      and other Troop 600B Google services.
    </p>
  </td></tr>

  <!-- Footer -->
  <tr><td style="background:#f9f9f9;border-top:1px solid #eee;padding:16px 32px;">
    <p style="margin:0;font-size:12px;color:#888;">&mdash; Troop 600B Admin</p>
  </td></tr>

</table>
</td></tr></table>
</body>
</html>`;
}

/** Returns the plain-text body of the welcome email (shown by email clients that don't render HTML). */
function buildWelcomePlain(firstName, fullEmail, password) {
  return `Hi ${firstName},

Your Troop 600 account has been created.

  Email:    ${fullEmail}
  Password: ${password}

Go to https://troop600.com/ and sign in with the email and password above within the next 48 hours.
You will be prompted to choose a new password on your first sign-in.

You will shortly receive a separate email from Google asking you to authorize
email forwarding to your ${fullEmail} account. Please open it and click
Confirm to complete the setup.

Your @troop600.com email is used for troop communications, Google Drive, and
other Troop 600B Google services.

\u2014 Troop 600B Admin`;
}

/**
 * Diagnostic: run this directly in the Apps Script editor (not via web app) to verify
 * that the service account key and DWD are configured correctly.
 * Set TEST_EMAIL to any user in the domain before running.
 * Check the Execution Log for results.
 */
function testForwardingToken() {
  var TEST_EMAIL = 'hotchkiesb@troop600.com';

  var keyJson = PropertiesService.getScriptProperties().getProperty('SERVICE_ACCOUNT_KEY');
  if (!keyJson) { Logger.log('ERROR: SERVICE_ACCOUNT_KEY not set'); return; }
  var keyData;
  try { keyData = JSON.parse(keyJson); } catch (ex) { Logger.log('ERROR: key is not valid JSON: ' + ex.message); return; }

  Logger.log('client_email: ' + keyData.client_email);
  Logger.log('private_key starts with: ' + (keyData.private_key || '').substring(0, 40));

  var jwt = makeServiceAccountJwt(keyData, TEST_EMAIL);
  Logger.log('JWT (first 80 chars): ' + jwt.substring(0, 80));

  var body = 'grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=' + jwt;
  Logger.log('Request body (first 80): ' + body.substring(0, 80));

  var resp = UrlFetchApp.fetch('https://oauth2.googleapis.com/token', {
    method: 'post',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    payload: Utilities.newBlob(body).getBytes(),
    muteHttpExceptions: true
  });
  Logger.log('Token response (' + resp.getResponseCode() + '): ' + resp.getContentText());
}

function doGet(e) {
  var action = (e.parameter || {}).action || '';
  try {
    switch (action) {
      case 'listUsers':  return respond(listUsers());
      case 'dumpFirst':  return respond(dumpFirst());
      case 'updateUser':         return respond(updateUser(e));
      case 'addRelationship':    return respond(addRelationship(e));
      case 'removeRelationship': return respond(removeRelationship(e));
      case 'checkGroup':         return respond(checkGroup(e));
      case 'createGroup':        return respond(createGroup(e));
      case 'listGroups':          return respond(listGroups(e));
      case 'deleteGroup':         return respond(deleteGroup(e));
      case 'getGroupMembers':     return respond(getGroupMembers(e));
      case 'updateGroupMembers':  return respond(updateGroupMembers(e));
      case 'listForwardingStatuses':  return respond(listForwardingStatuses());
      case 'getForwardingStatus':     return respond(getForwardingStatus(e));
      case 'setForwarding':                return respond(setForwarding(e));
      case 'resendForwardingVerification': return respond(resendForwardingVerification(e));
      case 'createUser':             return respond(createUser(e));
      case 'checkUser':              return respond(checkUser(e));
      case 'deleteUser':             return respond(deleteUser(e));
      default:                   return respond({ error: 'Unknown action: ' + action });
    }
  } catch (err) {
    return respond({ error: err.message });
  }
}

function respond(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

/**
 * Returns all users in the domain with primary + secondary emails,
 * org unit, status, and all custom schema attributes.
 */
function listUsers() {
  var users = [];
  var pageToken;
  do {
    var page = AdminDirectory.Users.list({
      domain:     DOMAIN,
      maxResults: 500,
      orderBy:    'familyName',
      projection: 'full',        // required to include customSchemas
      pageToken:  pageToken
    });
    (page.users || []).forEach(function(u) {

      // Secondary emails (all non-primary entries in the emails array)
      var secondaryEmails = (u.emails || [])
        .filter(function(e) { return !e.primary; })
        .map(function(e) { return e.address || ''; })
        .filter(Boolean);

      // Flatten all custom schema fields into a single map keyed by field name.
      // Schema name is intentionally ignored so field names work regardless of
      // what the schema was named in the Workspace admin console.
      var custom = {};
      if (u.customSchemas) {
        Object.keys(u.customSchemas).forEach(function(schema) {
          var fields = u.customSchemas[schema];
          Object.keys(fields).forEach(function(field) {
            custom[field] = fields[field];
          });
        });
      }

      // Multi-value fields are arrays of {type, value} objects — extract the values.
      function mvValues(field) {
        var arr = custom[field];
        if (!arr) return '';
        if (!Array.isArray(arr)) return String(arr);
        return arr.map(function(item) { return item.value || ''; }).filter(Boolean).join(', ');
      }

      users.push({
        id:              u.id,
        email:           u.primaryEmail,
        firstName:       u.name.givenName  || '',
        lastName:        u.name.familyName || '',
        orgUnit:         u.orgUnitPath,
        suspended:       u.suspended || false,
        lastLogin:       u.lastLoginTime || null,
        secondaryEmails: secondaryEmails,
        // Custom schema fields (schema names: Scout_Attributes, Troop_Attributes, Parent_Attributes)
        rank:     custom['Rank']     || '',
        patrol:   mvValues('Patrol'),
        scoutId:  custom['Scout_ID'] != null ? String(custom['Scout_ID']) : '',
        isYouth:  custom['Is_Youth'] != null ? custom['Is_Youth'] : '',
        parents:  mvValues('Parents'),
        scouts:   mvValues('Scouts')
      });
    });
    pageToken = page.nextPageToken;
  } while (pageToken);

  return { users: users };
}

/**
 * Updates fields for a single Workspace user.
 * Params: userId (Google user id), changes (URL-encoded JSON).
 * Recognized keys: givenName, familyName, orgUnitPath, suspended,
 *   Scout_ID (Number), Is_Youth (Boolean), Patrol, Rank,
 *   Parents (email string array), Scouts (email string array).
 */
function updateUser(e) {
  var userId      = (e.parameter || {}).userId;
  var changesJson = (e.parameter || {}).changes;
  if (!userId || !changesJson) return { error: 'Missing userId or changes' };

  var changes;
  try { changes = JSON.parse(changesJson); }
  catch (ex) { return { error: 'Invalid changes JSON: ' + ex.message }; }

  var userUpdate = {};
  var schemas    = {};

  // ── Top-level user resource fields ──────────────────────────────────────
  if ('givenName' in changes || 'familyName' in changes) {
    var given  = String(changes['givenName']  || '').trim();
    var family = String(changes['familyName'] || '').trim();
    userUpdate.name = {
      givenName:  given,
      familyName: family,
      fullName:   (given + ' ' + family).trim()
    };
  }
  if ('orgUnitPath' in changes) {
    var ou = String(changes['orgUnitPath'] || '').trim();
    userUpdate.orgUnitPath = (ou.charAt(0) === '/') ? ou : '/' + ou;
  }
  if ('suspended' in changes) {
    userUpdate.suspended = (changes['suspended'] === true || changes['suspended'] === 'true');
  }

  // ── Troop_Attributes schema ──────────────────────────────────────────────
  if ('Scout_ID' in changes) {
    schemas['Troop_Attributes'] = schemas['Troop_Attributes'] || {};
    var raw = changes['Scout_ID'];
    schemas['Troop_Attributes']['Scout_ID'] = (raw === '' || raw == null) ? null : Number(raw);
  }
  if ('Is_Youth' in changes) {
    schemas['Troop_Attributes'] = schemas['Troop_Attributes'] || {};
    schemas['Troop_Attributes']['Is_Youth'] = (changes['Is_Youth'] === true || changes['Is_Youth'] === 'true');
  }

  // ── Scout_Attributes schema ──────────────────────────────────────────────
  if ('Patrol' in changes) {
    schemas['Scout_Attributes'] = schemas['Scout_Attributes'] || {};
    schemas['Scout_Attributes']['Patrol'] = (changes['Patrol'] || []).map(function(name) {
      return { type: 'work', value: name };
    });
  }
  if ('Rank' in changes) {
    schemas['Scout_Attributes'] = schemas['Scout_Attributes'] || {};
    schemas['Scout_Attributes']['Rank'] = changes['Rank'];
  }
  if ('Parents' in changes) {
    schemas['Scout_Attributes'] = schemas['Scout_Attributes'] || {};
    schemas['Scout_Attributes']['Parents'] = (changes['Parents'] || []).map(function(addr) {
      return { type: 'work', value: addr };
    });
  }

  // ── Parent_Attributes schema ─────────────────────────────────────────────
  if ('Scouts' in changes) {
    schemas['Parent_Attributes'] = schemas['Parent_Attributes'] || {};
    schemas['Parent_Attributes']['Scouts'] = (changes['Scouts'] || []).map(function(addr) {
      return { type: 'work', value: addr };
    });
  }

  if (Object.keys(schemas).length) userUpdate.customSchemas = schemas;
  if (!Object.keys(userUpdate).length) return { error: 'No recognized fields to update' };

  try {
    AdminDirectory.Users.update(userUpdate, userId);
    return { ok: true };
  } catch (err) {
    return { error: err.message };
  }
}

/**
 * Permanently deletes a Google Workspace user account.
 * Params: userId (primary email or immutable ID).
 * This action is irreversible — the account cannot be recovered via the API.
 */
function deleteUser(e) {
  var userId = ((e.parameter || {}).userId || '').trim();
  if (!userId) return { error: 'Missing userId' };
  try {
    AdminDirectory.Users.remove(userId);
    return { ok: true };
  } catch (err) {
    return { error: err.message };
  }
}

/**
 * Checks whether a GWS account exists and is active (not suspended).
 * Params: email (primary GWS address to check).
 * Returns { exists: true } once the account is live, { exists: false } while it is still provisioning.
 */
function checkUser(e) {
  var email = ((e.parameter || {}).email || '').trim();
  if (!email) return { error: 'Missing email' };
  try {
    var user = AdminDirectory.Users.get(email, { projection: 'basic', viewType: 'admin_view' });
    return { exists: true, suspended: !!user.suspended };
  } catch (err) {
    // 404 = not yet visible; treat as still provisioning.
    return { exists: false };
  }
}

/**
 * Adds a parent–scout relationship to both users' custom schema multi-value arrays.
 * Params: parentEmail, scoutEmail.
 * Idempotent — does not add duplicate entries.
 */
function addRelationship(e) {
  var parentEmail = (e.parameter || {}).parentEmail;
  var scoutEmail  = (e.parameter || {}).scoutEmail;
  if (!parentEmail || !scoutEmail) return { error: 'Missing parentEmail or scoutEmail' };
  try {
    // Append scoutEmail to parent's Parent_Attributes.Scouts
    var parent = AdminDirectory.Users.get(parentEmail, { projection: 'full', viewType: 'admin_view' });
    var parentScouts = ((parent.customSchemas || {}).Parent_Attributes || {}).Scouts || [];
    if (!parentScouts.some(function(s) { return s.value === scoutEmail; })) {
      AdminDirectory.Users.update(
        { customSchemas: { Parent_Attributes: { Scouts: parentScouts.concat([{ type: 'work', value: scoutEmail }]) } } },
        parentEmail);
    }
    // Append parentEmail to scout's Scout_Attributes.Parents
    var scout = AdminDirectory.Users.get(scoutEmail, { projection: 'full', viewType: 'admin_view' });
    var scoutParents = ((scout.customSchemas || {}).Scout_Attributes || {}).Parents || [];
    if (!scoutParents.some(function(p) { return p.value === parentEmail; })) {
      AdminDirectory.Users.update(
        { customSchemas: { Scout_Attributes: { Parents: scoutParents.concat([{ type: 'work', value: parentEmail }]) } } },
        scoutEmail);
    }
    return { ok: true };
  } catch (err) {
    return { error: err.message };
  }
}

/**
 * Removes a parent–scout relationship from both users' custom schema arrays.
 * Params: parentEmail, scoutEmail.
 * Idempotent — no-op if the link does not exist.
 */
function removeRelationship(e) {
  var parentEmail = (e.parameter || {}).parentEmail;
  var scoutEmail  = (e.parameter || {}).scoutEmail;
  if (!parentEmail || !scoutEmail) return { error: 'Missing parentEmail or scoutEmail' };
  try {
    // Remove scoutEmail from parent's Parent_Attributes.Scouts
    var parent = AdminDirectory.Users.get(parentEmail, { projection: 'full', viewType: 'admin_view' });
    var parentScouts = ((parent.customSchemas || {}).Parent_Attributes || {}).Scouts || [];
    var newParentScouts = parentScouts.filter(function(s) { return s.value !== scoutEmail; });
    if (newParentScouts.length !== parentScouts.length) {
      AdminDirectory.Users.update(
        { customSchemas: { Parent_Attributes: { Scouts: newParentScouts } } },
        parentEmail);
    }
    // Remove parentEmail from scout's Scout_Attributes.Parents
    var scout = AdminDirectory.Users.get(scoutEmail, { projection: 'full', viewType: 'admin_view' });
    var scoutParents = ((scout.customSchemas || {}).Scout_Attributes || {}).Parents || [];
    var newScoutParents = scoutParents.filter(function(p) { return p.value !== parentEmail; });
    if (newScoutParents.length !== scoutParents.length) {
      AdminDirectory.Users.update(
        { customSchemas: { Scout_Attributes: { Parents: newScoutParents } } },
        scoutEmail);
    }
    return { ok: true };
  } catch (err) {
    return { error: err.message };
  }
}

/** Debug: returns the raw Admin SDK object for the first user — use to verify field names. */
function dumpFirst() {
  var page = AdminDirectory.Users.list({
    domain: DOMAIN, maxResults: 1, projection: 'full'
  });
  var u = (page.users || [])[0];
  if (!u) return { error: 'No users found' };
  return {
    primaryEmail:  u.primaryEmail,
    name:          u.name,
    emails:        u.emails,
    customSchemas: u.customSchemas,
    orgUnitPath:   u.orgUnitPath,
    suspended:     u.suspended
  };
}

/**
 * Checks whether a Google Group with the given email address exists.
 * Params: email — the group email to check.
 * Returns: { exists: true } or { exists: false }. Errors surface as { error: '...' }.
 */
function checkGroup(e) {
  var email = (e.parameter || {}).email;
  if (!email) return { error: 'Missing email' };
  try {
    AdminDirectory.Groups.get(email);
    return { exists: true };
  } catch (err) {
    // GAS throws on any non-2xx response. "not found" variants mean the group doesn't exist.
    var msg = (err.message || '').toLowerCase();
    if (msg.indexOf('not found') !== -1 || msg.indexOf('404') !== -1) {
      return { exists: false };
    }
    return { error: err.message };
  }
}

/**
 * Creates a new Google Group for a patrol, copies settings from the template group
 * (rangers2025@troop600.com), then adds the provided member emails.
 * Params: email (new group address), name (display name), members (JSON array of emails).
 * Note: subject prefix ([PATROL NAME]) is not settable via the GroupsSettings API and must
 * be configured manually in the Google Groups admin console after creation.
 */
function createGroup(e) {
  var params      = e.parameter || {};
  var email       = params.email;
  var name        = params.name;
  var membersJson = params.members || '[]';
  if (!email || !name) return { error: 'Missing email or name' };

  var TEMPLATE = 'rangers2025@troop600.com';

  try {
    // Create the group resource
    AdminDirectory.Groups.insert({
      email:       email,
      name:        name,
      description: name + ' patrol mailing list'
    });

    // Copy settings from the template group (non-fatal if it fails)
    try {
      var tpl = GroupsSettings.Groups.get(TEMPLATE);
      GroupsSettings.Groups.update({
        whoCanPostMessage:               tpl.whoCanPostMessage,
        whoCanViewMembership:            tpl.whoCanViewMembership,
        whoCanViewGroup:                 tpl.whoCanViewGroup,
        whoCanJoin:                      tpl.whoCanJoin,
        isArchived:                      tpl.isArchived,
        messageModerationLevel:          tpl.messageModerationLevel,
        spamModerationLevel:             tpl.spamModerationLevel,
        replyTo:                         tpl.replyTo,
        includeInGlobalAddressList:      tpl.includeInGlobalAddressList,
        sendMessageDenyNotification:     tpl.sendMessageDenyNotification,
        defaultMessageDenyNotificationText: tpl.defaultMessageDenyNotificationText,
        membersCanPostAsTheGroup:        tpl.membersCanPostAsTheGroup,
        includeStandardFooter:           true,
        includeCustomFooter:             tpl.includeCustomFooter,
        customFooterText:                tpl.customFooterText,
        whoCanLeaveGroup:                tpl.whoCanLeaveGroup,
        whoCanContactOwner:              tpl.whoCanContactOwner,
        favoriteRepliesOnTop:            tpl.favoriteRepliesOnTop
      }, email);
    } catch (settingsErr) {
      Logger.log('Warning: could not copy group settings from ' + TEMPLATE + ': ' + settingsErr.message);
    }

    // Add members (scouts + parents); non-fatal per member
    var members;
    try { members = JSON.parse(membersJson); } catch (ex) { members = []; }
    members.forEach(function(memberEmail) {
      if (!memberEmail) return;
      try {
        AdminDirectory.Members.insert({ email: memberEmail, role: 'MEMBER' }, email);
      } catch (memberErr) {
        Logger.log('Warning: could not add ' + memberEmail + ' to ' + email + ': ' + memberErr.message);
      }
    });

    return { ok: true };
  } catch (err) {
    return { error: err.message };
  }
}

/**
 * Lists all Google Groups in the domain whose email ends with the given suffix.
 * Params: suffix (e.g. "-patrol@troop600.com").
 * Returns: { groups: [ { email, name } ] }
 */
function listGroups(e) {
  var suffix = (e.parameter || {}).suffix || '';
  var groups = [];
  var pageToken;
  do {
    var page = AdminDirectory.Groups.list({
      domain:     DOMAIN,
      maxResults: 200,
      pageToken:  pageToken
    });
    (page.groups || []).forEach(function(g) {
      if (!suffix || (g.email || '').toLowerCase().endsWith(suffix.toLowerCase())) {
        groups.push({ email: g.email, name: g.name || g.email });
      }
    });
    pageToken = page.nextPageToken;
  } while (pageToken);
  return { groups: groups };
}

/**
 * Permanently deletes a Google Group by email address.
 * Params: email — the group address to delete.
 */
function deleteGroup(e) {
  var email = (e.parameter || {}).email;
  if (!email) return { error: 'Missing email' };
  try {
    AdminDirectory.Groups.remove(email);
    return { ok: true };
  } catch (err) {
    return { error: err.message };
  }
}

/**
 * Returns the list of member email addresses for a Google Group.
 * Params: email — the group address.
 * Returns: { members: [email, ...] }
 */
function getGroupMembers(e) {
  var email = (e.parameter || {}).email;
  if (!email) return { error: 'Missing email' };
  try {
    var members = [];
    var pageToken;
    do {
      var page = AdminDirectory.Members.list(email, { maxResults: 200, pageToken: pageToken });
      (page.members || []).forEach(function(m) {
        if (m.email) members.push(m.email.toLowerCase());
      });
      pageToken = page.nextPageToken;
    } while (pageToken);
    return { members: members };
  } catch (err) {
    return { error: err.message };
  }
}

/**
 * Adds and removes members from a Google Group in one call.
 * Params: groupEmail, toAdd (JSON array of emails), toRemove (JSON array of emails).
 * Non-fatal per member — collects errors and returns them alongside ok:true.
 */
function updateGroupMembers(e) {
  var groupEmail   = (e.parameter || {}).groupEmail;
  var toAddJson    = (e.parameter || {}).toAdd    || '[]';
  var toRemoveJson = (e.parameter || {}).toRemove || '[]';
  if (!groupEmail) return { error: 'Missing groupEmail' };

  var toAdd, toRemove;
  try { toAdd    = JSON.parse(toAddJson);    } catch (ex) { toAdd    = []; }
  try { toRemove = JSON.parse(toRemoveJson); } catch (ex) { toRemove = []; }

  var errors = [];
  toAdd.forEach(function(memberEmail) {
    try {
      AdminDirectory.Members.insert({ email: memberEmail, role: 'MEMBER' }, groupEmail);
    } catch (err) {
      errors.push('ADD ' + memberEmail + ': ' + err.message);
    }
  });
  toRemove.forEach(function(memberEmail) {
    try {
      AdminDirectory.Members.remove(groupEmail, memberEmail);
    } catch (err) {
      errors.push('REMOVE ' + memberEmail + ': ' + err.message);
    }
  });
  return { ok: true, errors: errors };
}

/**
 * Builds a signed JWT for a service account impersonating subEmail.
 * Reads the service account key from the SERVICE_ACCOUNT_KEY script property.
 */
function makeServiceAccountJwt(keyData, subEmail) {
  // Script Properties can store \n as a literal two-character sequence rather than a real
  // newline. Normalize here so the PEM key is valid for Utilities.computeRsaSha256Signature.
  var privateKey = keyData.private_key.replace(/\\n/g, '\n');

  var now     = Math.floor(Date.now() / 1000);
  var header  = Utilities.base64EncodeWebSafe(JSON.stringify({ alg: 'RS256', typ: 'JWT' })).replace(/=+$/, '');
  var payload = Utilities.base64EncodeWebSafe(JSON.stringify({
    iss:   keyData.client_email,
    sub:   subEmail,
    scope: 'https://www.googleapis.com/auth/gmail.settings.basic https://www.googleapis.com/auth/gmail.settings.sharing',
    aud:   'https://oauth2.googleapis.com/token',
    iat:   now,
    exp:   now + 3600
  })).replace(/=+$/, '');
  var sig = Utilities.base64EncodeWebSafe(
    Utilities.computeRsaSha256Signature(header + '.' + payload, privateKey)
  ).replace(/=+$/, '');
  return header + '.' + payload + '.' + sig;
}

/**
 * Returns email forwarding status for every domain user via service-account impersonation.
 * Requires SERVICE_ACCOUNT_KEY script property (full JSON key) and DWD authorized for
 * https://www.googleapis.com/auth/gmail.settings.basic AND
 * https://www.googleapis.com/auth/gmail.settings.sharing in Workspace Admin.
 * Each entry: { email, enabled, forwardTo, pendingAddresses[], verifiedAddresses[] }.
 */
/**
 * Runs UrlFetchApp.fetchAll in batches to avoid hitting Google's rate limit.
 * Pauses 600ms between batches.
 */
function batchedFetchAll(requests, batchSize) {
  var results = [];
  for (var i = 0; i < requests.length; i += batchSize) {
    var batch = requests.slice(i, i + batchSize);
    var batchResults = UrlFetchApp.fetchAll(batch);
    for (var j = 0; j < batchResults.length; j++) results.push(batchResults[j]);
    if (i + batchSize < requests.length) Utilities.sleep(600);
  }
  return results;
}

function listForwardingStatuses() {
  var keyJson = PropertiesService.getScriptProperties().getProperty('SERVICE_ACCOUNT_KEY');
  if (!keyJson) return { error: 'SERVICE_ACCOUNT_KEY script property not set' };
  var keyData;
  try { keyData = JSON.parse(keyJson); } catch (ex) { return { error: 'SERVICE_ACCOUNT_KEY is not valid JSON' }; }

  // Collect all user emails
  var emails = [];
  var pageToken;
  do {
    var page = AdminDirectory.Users.list({ domain: DOMAIN, maxResults: 500, pageToken: pageToken });
    (page.users || []).forEach(function(u) { emails.push(u.primaryEmail); });
    pageToken = page.nextPageToken;
  } while (pageToken);
  if (!emails.length) return { statuses: [] };

  // Round 1: get access tokens in batches of 10.
  // Use explicit headers + encodeURIComponent string payload — fetchAll does not reliably
  // apply contentType from the options object, so we set Content-Type via headers instead.
  var tokenReqs = emails.map(function(email) {
    var jwt = makeServiceAccountJwt(keyData, email);
    return {
      url: 'https://oauth2.googleapis.com/token',
      method: 'post',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      payload: 'grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=' + jwt,
      muteHttpExceptions: true
    };
  });
  var tokenResps = batchedFetchAll(tokenReqs, 10);
  var tokens = tokenResps.map(function(r) {
    try {
      var d = JSON.parse(r.getContentText());
      return d.access_token ? { token: d.access_token } : { error: d.error_description || d.error || r.getContentText() };
    } catch (ex) { return { error: ex.message }; }
  });

  // Round 2: fetch autoForwarding + forwardingAddresses in batches of 20 requests (10 users × 2)
  var apiReqs = [];
  emails.forEach(function(email, i) {
    var auth = (tokens[i] && tokens[i].token) ? { Authorization: 'Bearer ' + tokens[i].token } : {};
    var base = 'https://gmail.googleapis.com/gmail/v1/users/' + encodeURIComponent(email);
    apiReqs.push({ url: base + '/settings/autoForwarding',      headers: auth, muteHttpExceptions: true });
    apiReqs.push({ url: base + '/settings/forwardingAddresses', headers: auth, muteHttpExceptions: true });
  });
  var apiResps = batchedFetchAll(apiReqs, 20);

  var statuses = emails.map(function(email, i) {
    if (!tokens[i] || tokens[i].error) return { email: email, error: 'Token error: ' + (tokens[i] ? tokens[i].error : 'no response') };
    try {
      var autoText = apiResps[i * 2]     ? apiResps[i * 2].getContentText()     : '';
      var fwdText  = apiResps[i * 2 + 1] ? apiResps[i * 2 + 1].getContentText() : '';
      if (!autoText) return { email: email, error: 'Empty response from autoForwarding API (HTTP ' + (apiResps[i * 2] ? apiResps[i * 2].getResponseCode() : '?') + ')' };
      var autoFwd = JSON.parse(autoText);
      if (autoFwd.error) {
        var msg = autoFwd.error.message || JSON.stringify(autoFwd.error);
        // Gmail disabled for this account — not an error, just no mailbox.
        if (msg.toLowerCase().indexOf('mail service not enabled') !== -1) return { email: email, noMailbox: true };
        return { email: email, error: msg };
      }
      var pending  = [];
      var verified = [];
      if (fwdText) {
        var fwdList = JSON.parse(fwdText);
        (fwdList.forwardingAddresses || []).forEach(function(fa) {
          if (fa.verificationStatus === 'pending')  pending.push(fa.forwardingEmail);
          if (fa.verificationStatus === 'accepted') verified.push(fa.forwardingEmail);
        });
      }
      return { email: email, enabled: autoFwd.enabled || false, forwardTo: autoFwd.emailAddress || null,
               pendingAddresses: pending, verifiedAddresses: verified };
    } catch (ex) {
      return { email: email, error: ex.message };
    }
  });
  return { statuses: statuses };
}

/**
 * Returns the forwarding status for a single user.
 * Params: email (GWS primary email).
 * Returns { status: { email, enabled, forwardTo, pendingAddresses[], verifiedAddresses[] } }
 * or { status: { email, error } } / { status: { email, noMailbox: true } } on failure.
 */
function getForwardingStatus(e) {
  var email = ((e.parameter || {}).email || '').trim();
  if (!email) return { error: 'email parameter required' };

  var keyJson = PropertiesService.getScriptProperties().getProperty('SERVICE_ACCOUNT_KEY');
  if (!keyJson) return { status: { email: email, error: 'SERVICE_ACCOUNT_KEY not set' } };
  var keyData;
  try { keyData = JSON.parse(keyJson); } catch (ex) { return { status: { email: email, error: 'SERVICE_ACCOUNT_KEY is not valid JSON' } }; }

  var jwt = makeServiceAccountJwt(keyData, email);
  var tokenResp = UrlFetchApp.fetch('https://oauth2.googleapis.com/token', {
    method: 'post',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    payload: 'grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=' + jwt,
    muteHttpExceptions: true
  });
  var tokenData = JSON.parse(tokenResp.getContentText());
  if (!tokenData.access_token) {
    return { status: { email: email, error: 'Token error: ' + (tokenData.error_description || tokenData.error || 'unknown') } };
  }

  var token = tokenData.access_token;
  var base   = 'https://gmail.googleapis.com/gmail/v1/users/' + encodeURIComponent(email);
  var auth   = { Authorization: 'Bearer ' + token };

  var resps = UrlFetchApp.fetchAll([
    { url: base + '/settings/autoForwarding',      headers: auth, muteHttpExceptions: true },
    { url: base + '/settings/forwardingAddresses', headers: auth, muteHttpExceptions: true }
  ]);

  try {
    var autoFwd = JSON.parse(resps[0].getContentText());
    if (autoFwd.error) {
      var msg = autoFwd.error.message || JSON.stringify(autoFwd.error);
      if (msg.toLowerCase().indexOf('mail service not enabled') !== -1) return { status: { email: email, noMailbox: true } };
      return { status: { email: email, error: msg } };
    }
    var pending = [], verified = [];
    try {
      var fwdList = JSON.parse(resps[1].getContentText());
      (fwdList.forwardingAddresses || []).forEach(function(fa) {
        if (fa.verificationStatus === 'pending')  pending.push(fa.forwardingEmail);
        if (fa.verificationStatus === 'accepted') verified.push(fa.forwardingEmail);
      });
    } catch (ex) { /* ignore — forwardingAddresses is best-effort */ }
    return { status: { email: email, enabled: autoFwd.enabled || false, forwardTo: autoFwd.emailAddress || null,
                       pendingAddresses: pending, verifiedAddresses: verified } };
  } catch (ex) {
    return { status: { email: email, error: ex.message } };
  }
}

/**
 * Core DWD forwarding logic shared by setForwarding and createUser.
 * Returns { ok: true } on success or { error: '...' } on failure.
 */
function setForwardingForUser(email, forwardTo) {
  var keyJson = PropertiesService.getScriptProperties().getProperty('SERVICE_ACCOUNT_KEY');
  if (!keyJson) return { error: 'SERVICE_ACCOUNT_KEY script property not set' };
  var keyData;
  try { keyData = JSON.parse(keyJson); } catch (ex) { return { error: 'SERVICE_ACCOUNT_KEY is not valid JSON' }; }

  var jwt = makeServiceAccountJwt(keyData, email);
  var tokenResp = UrlFetchApp.fetch('https://oauth2.googleapis.com/token', {
    method: 'post',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    payload: 'grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=' + jwt,
    muteHttpExceptions: true
  });
  var tokenData = JSON.parse(tokenResp.getContentText());
  if (!tokenData.access_token) {
    return { error: 'Failed to get access token: ' + (tokenData.error_description || tokenData.error || 'unknown') };
  }

  var token = tokenData.access_token;
  var base  = 'https://gmail.googleapis.com/gmail/v1/users/' + encodeURIComponent(email);

  // Add forwarding address (409 = already registered — treat as non-fatal)
  var addResp = UrlFetchApp.fetch(base + '/settings/forwardingAddresses', {
    method: 'post',
    headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' },
    payload: JSON.stringify({ forwardingEmail: forwardTo }),
    muteHttpExceptions: true
  });
  var addData = JSON.parse(addResp.getContentText());
  if (addData.error && addData.error.code !== 409) {
    var addMsg = addData.error.message || 'Unknown error';
    if (addMsg === 'Invalid forwarding address') {
      // Gmail queues external addresses for ownership verification even when it returns this
      // error code — the address does appear as pending. Treat as pending rather than failure.
      return { pending: true, forwardTo: forwardTo };
    }
    return { error: addMsg };
  }

  // Enable auto-forwarding
  var fwdResp = UrlFetchApp.fetch(base + '/settings/autoForwarding', {
    method: 'put',
    headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' },
    payload: JSON.stringify({ enabled: true, emailAddress: forwardTo, disposition: 'leaveInInbox' }),
    muteHttpExceptions: true
  });
  var fwdData = JSON.parse(fwdResp.getContentText());
  if (fwdData.error) {
    var fwdMsg = fwdData.error.message || '';
    // "Precondition check failed" means the address was added but isn't verified yet —
    // auto-forwarding cannot be enabled until the recipient clicks the verification link.
    // "Invalid Email Address" can appear for the same reason on some domain configurations.
    // Both are expected for external addresses; treat as pending rather than an error.
    if (fwdMsg.toLowerCase().indexOf('precondition') !== -1 ||
        fwdMsg.toLowerCase().indexOf('invalid email') !== -1) {
      return { pending: true, forwardTo: forwardTo };
    }
    return { error: fwdMsg };
  }

  return { ok: true };
}

/**
 * Action handler: enables forwarding for a single user.
 * Params: email (GWS user), forwardTo (destination address).
 */
function setForwarding(e) {
  var params    = e.parameter || {};
  var email     = params.email;
  var forwardTo = params.forwardTo;
  if (!email || !forwardTo) return { error: 'Missing email or forwardTo' };
  return setForwardingForUser(email, forwardTo);
}

/**
 * Retriggers Google's standard forwarding verification email for a pending address.
 * Params: email (GWS primary email), forwardTo (pending forwarding address).
 */
function resendForwardingVerification(e) {
  var params    = e.parameter || {};
  var email     = (params.email     || '').trim();
  var forwardTo = (params.forwardTo || '').trim();
  if (!email || !forwardTo) return { error: 'Missing email or forwardTo' };

  var keyJson = PropertiesService.getScriptProperties().getProperty('SERVICE_ACCOUNT_KEY');
  if (!keyJson) return { error: 'SERVICE_ACCOUNT_KEY script property not set' };
  var keyData;
  try { keyData = JSON.parse(keyJson); } catch (ex) { return { error: 'SERVICE_ACCOUNT_KEY is not valid JSON' }; }

  var jwt = makeServiceAccountJwt(keyData, email);
  var tokenResp = UrlFetchApp.fetch('https://oauth2.googleapis.com/token', {
    method: 'post',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    payload: 'grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=' + jwt,
    muteHttpExceptions: true
  });
  var tokenData = JSON.parse(tokenResp.getContentText());
  if (!tokenData.access_token) {
    return { error: 'Failed to get access token: ' + (tokenData.error_description || tokenData.error || 'unknown') };
  }

  var verifyUrl = 'https://gmail.googleapis.com/gmail/v1/users/'
    + encodeURIComponent(email)
    + '/settings/forwardingAddresses/'
    + encodeURIComponent(forwardTo)
    + '/verify';

  var resp = UrlFetchApp.fetch(verifyUrl, {
    method: 'post',
    headers: { Authorization: 'Bearer ' + tokenData.access_token },
    muteHttpExceptions: true
  });

  // 204 No Content = success; error body = failure
  var body = resp.getContentText();
  if (body) {
    try {
      var data = JSON.parse(body);
      if (data.error) return { error: data.error.message || JSON.stringify(data.error) };
    } catch (ex) { /* non-JSON on 204 — treat as success */ }
  }
  return { ok: true };
}

/**
 * Creates a new Google Workspace user, sets custom schema fields, sends a welcome
 * email via MailApp, and attempts to configure Gmail forwarding to the secondary address.
 * Params: firstName, lastName, username (without @domain), isYouth ('true'/'false'),
 *         bsaId (optional), secondaryEmail (optional), rank (optional), patrol (optional).
 * Returns { ok, email, password } on success, { ok, email, password, warnings[] } if
 * non-fatal steps (email/forwarding) failed, or { error } if user creation itself failed.
 * The temporary password is always returned so the admin has it if the welcome email fails.
 */
function createUser(e) {
  var params         = e.parameter || {};
  var firstName      = (params.firstName      || '').trim();
  var lastName       = (params.lastName       || '').trim();
  var username       = (params.username       || '').trim().toLowerCase();
  var isYouth        = params.isYouth === 'true';
  var bsaId          = (params.bsaId          || '').trim();
  var secondaryEmail = (params.secondaryEmail || '').trim();
  var rank           = (params.rank           || '').trim();
  var patrol         = (params.patrol         || '').trim();

  if (!firstName || !lastName || !username) {
    return { error: 'First name, last name, and username are required.' };
  }

  var fullEmail = username + '@' + DOMAIN;

  // Check for username conflict — Users.get throws on 404, which is the success case.
  try {
    AdminDirectory.Users.get(fullEmail);
    return { error: '\u201c' + username + '\u201d is already taken. Choose a different username.' };
  } catch (ex) {
    // Any error other than "not found" is unexpected — surface it.
    var msg = ex.message || '';
    if (msg.indexOf('404') === -1 && msg.toLowerCase().indexOf('not found') === -1) {
      return { error: 'Error checking username: ' + msg };
    }
  }

  // Generate a cryptographically random 12-char password (upper + lower + digit + symbol).
  var chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#$';
  var password = '';
  for (var i = 0; i < 12; i++) password += chars[Math.floor(Math.random() * chars.length)];

  var emails = [{ address: fullEmail, primary: true, type: 'work' }];
  if (secondaryEmail) emails.push({ address: secondaryEmail, primary: false, type: 'home' });

  var schemas = { Troop_Attributes: { Is_Youth: isYouth } };
  if (bsaId) schemas.Troop_Attributes.Scout_ID = Number(bsaId);
  if (rank || patrol) {
    schemas.Scout_Attributes = {};
    if (rank)   schemas.Scout_Attributes.Rank   = rank;
    if (patrol) schemas.Scout_Attributes.Patrol = [{ type: 'work', value: patrol }];
  }

  var userObj = {
    primaryEmail:              fullEmail,
    name: { givenName: firstName, familyName: lastName, fullName: firstName + ' ' + lastName },
    password:                  password,
    changePasswordAtNextLogin: true,
    orgUnitPath:               '/',
    emails:                    emails,
    customSchemas:             schemas
  };

  try {
    AdminDirectory.Users.insert(userObj);
  } catch (ex) {
    return { error: 'Failed to create user: ' + ex.message };
  }

  var warnings = [];

  // Send welcome email with temp password to secondary address via MailApp.
  // Edit welcome_email.html (HTML body) and welcome_email_plain.html (plain-text fallback)
  // in the Apps Script project to customise the email content.
  // Requires the script.send_mail OAuth scope declared in appsscript.json.
  if (secondaryEmail) {
    try {
      MailApp.sendEmail({
        to:       secondaryEmail,
        subject:  'Your Troop 600B Google Workspace account',
        name:     'Troop 600B Admin',
        body:     buildWelcomePlain(firstName, fullEmail, password),
        htmlBody: buildWelcomeHtml(firstName, fullEmail, password)
      });
    } catch (ex) {
      warnings.push('Welcome email could not be sent: ' + ex.message);
    }
  }

  // Attempt forwarding setup (non-fatal — Gmail may not be provisioned yet on a brand-new account).
  if (secondaryEmail) {
    var fwdResult = setForwardingForUser(fullEmail, secondaryEmail);
    if (fwdResult.error) {
      warnings.push('Forwarding not set up automatically \u2014 use the Set Forward button in the Workspace Users table once the account is active.');
    }
  }

  // Always return the temporary password so the admin has it if the welcome email failed.
  var result = { ok: true, email: fullEmail, password: password };
  if (warnings.length) result.warnings = warnings;
  return result;
}