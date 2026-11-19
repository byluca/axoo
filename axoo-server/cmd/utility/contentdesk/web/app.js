const state = { actorId: localStorage.getItem("axoo.contentdesk.actor") || "", data: null, reply: new Map(), cursor: "", request: null, searchIndex: [], matches: [] };
const actor = document.querySelector("#actor");
const actorSearch = document.querySelector("#actor-search");
const feed = document.querySelector("#feed");
const morePosts = document.querySelector("#more-posts");
const statusBox = document.querySelector("#status");
const dateFormatter = new Intl.DateTimeFormat("it-IT", {day:"2-digit", month:"2-digit", hour:"2-digit", minute:"2-digit"});
const normalize = value => value.normalize("NFD").replace(/[\u0300-\u036f@]/g, "").toLowerCase().trim();
const draftKey = id => `axoo.contentdesk.draft.${id || "post"}`;
function readDraft(id) { try { return JSON.parse(localStorage.getItem(draftKey(id))) || {}; } catch { return {}; } }
function writeDraft(id, draft) { localStorage.setItem(draftKey(id), JSON.stringify(draft)); }
function escapeHtml(value = "") { return String(value ?? "").replace(/[&<>"']/g, c => ({"&":"&amp;", "<":"&lt;", ">":"&gt;", '"':"&quot;", "'":"&#39;"}[c])); }
function formatDate(value) { return dateFormatter.format(new Date(value)); }
function notice(message) {
  statusBox.textContent = message; statusBox.classList.add("visible"); clearTimeout(notice.timer);
  notice.timer = setTimeout(() => statusBox.classList.remove("visible"), 2600);
}
async function api(path, options = {}) {
  const response = await fetch(path, {...options, headers:{"Content-Type":"application/json", ...(options.headers || {})}});
  const body = response.status === 204 ? null : await response.json().catch(() => ({}));
  if (!response.ok) { const error = new Error(body?.error || `Errore HTTP ${response.status}`); error.status = response.status; throw error; }
  return body;
}
async function load({profiles = false, append = false} = {}) {
  state.request?.abort();
  const controller = new AbortController(); state.request = controller;
  feed.setAttribute("aria-busy", "true"); morePosts.disabled = true;
  if (!state.data) feed.innerHTML = '<div class="card empty">Caricamento…</div>';
  const query = new URLSearchParams(); if (state.actorId) query.set("actor", state.actorId);
  if (append && state.cursor) query.set("cursor", state.cursor);
  const bootstrap = profiles || !state.data;
  try {
    const data = await api(`${bootstrap ? "/api/bootstrap" : "/api/posts"}?${query}`, {signal:controller.signal});
    if (controller.signal.aborted) return;
    if (bootstrap) {
      state.data = data;
      if (!data.profiles.some(p => p.id === state.actorId)) state.actorId = data.profiles[0]?.id || "";
      localStorage.setItem("axoo.contentdesk.actor", state.actorId); renderSelectors();
    } else {
      const seen = new Set(append ? state.data.posts.map(p => p.id) : []);
      state.data.posts = [...(append ? state.data.posts : []), ...(data.posts || []).filter(p => !seen.has(p.id))];
    }
    state.cursor = data.next_cursor || ""; renderFeed();
  } catch (error) {
    if (controller.signal.aborted) return;
    if (bootstrap && error.status === 403 && state.actorId) { state.actorId = ""; return load({profiles:true}); }
    if (!state.data) feed.innerHTML = '<div class="card empty">Caricamento non riuscito. Premi Aggiorna per riprovare.</div>';
    throw error;
  } finally {
    if (state.request === controller) { feed.setAttribute("aria-busy", "false"); morePosts.disabled = false; morePosts.hidden = !state.cursor; }
  }
}
function renderActorOptions() {
  const terms = normalize(actorSearch.value).split(/\s+/).filter(Boolean);
  const matches = state.searchIndex.filter(p => terms.every(term => p.search.includes(term)));
  state.matches = matches;
  const current = state.searchIndex.find(p => p.id === state.actorId);
  const options = current && !matches.includes(current) ? [{...current, pinned:true}, ...matches] : matches;
  actor.replaceChildren();
  for (const [gender, label] of [["male", "Uomini"], ["female", "Donne"], ["", "Altri profili"]]) {
    const profiles = options.filter(p => (p.gender || "") === gender);
    if (!profiles.length) continue;
    const group = document.createElement("optgroup"); group.label = label;
    group.append(...profiles.map(p => new Option(`${p.pinned ? "Attivo · " : ""}@${p.nickname} · ${p.university_name}`, p.id)));
    actor.append(group);
  }
  if (!matches.length) { const empty = new Option("Nessun risultato", ""); empty.disabled = true; actor.add(empty); }
  actor.value = state.actorId;
}
function renderSelectors() {
  state.searchIndex = state.data.profiles.map(p => ({...p, search:normalize(`${p.nickname} ${p.university_name}`)}));
  renderActorOptions();
  const university = document.querySelector("#university"), previous = university.value;
  university.replaceChildren(...state.data.universities.map(u => new Option(u.name, u.id)));
  if (state.data.universities.some(u => u.id === previous)) university.value = previous;
  const bulkUniversity = document.querySelector("#bulk-university"), previousBulk = bulkUniversity.value;
  bulkUniversity.replaceChildren(...state.data.universities.map(u => new Option(u.name, u.id)));
  if (state.data.universities.some(u => u.id === previousBulk)) bulkUniversity.value = previousBulk;
  document.querySelector('#post-form button[type="submit"]').disabled = !state.actorId;
  document.dispatchEvent(new Event("desk-profiles-loaded"));
}
function voteButton(kind, target, current, count) {
  const value = kind === "up" ? 1 : -1, active = current === (value === 1 ? "upvote" : "downvote");
  return `<button class="vote ${active ? `active-${kind}` : ""}" data-action="vote" data-kind="${target.kind}" data-id="${target.id}" data-value="${active ? 0 : value}">${value === 1 ? "▲" : "▼"} ${count || 0}</button>`;
}
function votesHTML(kind, item) { return voteButton("up", {kind, id:item.id}, item.my_vote, item.upvotes) + voteButton("down", {kind, id:item.id}, item.my_vote, item.downvotes); }
function mediaHTML(media) { return media?.url && /^https:\/\//.test(media.url) ? `<img class="media" src="${escapeHtml(media.url)}" alt="${escapeHtml(media.type || "Allegato")}" loading="lazy" decoding="async" referrerpolicy="no-referrer">` : ""; }
function pollHTML(post) {
  const poll = post.poll;
  if (!poll) return "";
  const exact = poll.options.map(option => poll.total_votes ? option.votes * 100 / poll.total_votes : 0);
  const percentages = exact.map(Math.floor);
  const remainder = poll.total_votes ? 100 - percentages.reduce((sum, value) => sum + value, 0) : 0;
  exact.map((value, index) => index).sort((a, b) => (exact[b] - percentages[b]) - (exact[a] - percentages[a]))
    .slice(0, remainder).forEach(index => percentages[index]++);
  return `<div class="poll">${poll.options.map((option, index) => `<div class="poll-row">
    <button type="button" class="poll-option" data-action="poll-vote" data-post="${post.id}" data-option="${option.id}" ${poll.my_option_id || post.pollSending ? "disabled" : ""}>
      <span class="poll-check" aria-hidden="true">${poll.my_option_id === option.id ? "✓" : ""}</span>
      <span>${deskEmojis.renderBody(option.text, option.entities)}</span>${poll.my_option_id ? `<span>${percentages[index]}%</span>` : ""}
    </button>${poll.my_option_id ? `<progress class="poll-bar" max="100" value="${percentages[index]}" aria-label="Risultato opzione ${index + 1}"></progress>` : ""}
    </div>`).join("")}<p class="hint">${poll.total_votes} voti · Voto definitivo</p></div>`;
}
function commentsHTML(post) {
  const comments = post.comments || [], byID = new Map(comments.map(c => [c.id, c])), children = new Map(), seen = new Set(), ordered = [];
  for (const comment of comments) { const parent = byID.has(comment.parent_comment_id) ? comment.parent_comment_id : ""; if (!children.has(parent)) children.set(parent, []); children.get(parent).push(comment); }
  function add(parent) { for (const c of children.get(parent) || []) { if (seen.has(c.id)) continue; seen.add(c.id); ordered.push(c); add(c.id); } }
  add(""); for (const c of comments) if (!seen.has(c.id)) { seen.add(c.id); ordered.push(c); add(c.id); }
  return ordered.map(comment => {
    const parent = byID.get(comment.parent_comment_id);
    return `<div class="comment ${parent ? "reply-comment" : ""}" data-comment="${comment.id}">
      <div class="meta"><span class="nickname">@${escapeHtml(comment.nickname)}</span><span>${formatDate(comment.created_at)}</span></div>
      ${parent ? `<p class="comment-reply-context">Risposta a @${escapeHtml(parent.nickname)}</p>` : ""}
      <div class="body">${deskEmojis.renderBody(comment.body, comment.entities)}</div>${mediaHTML(comment.media)}
      <div class="actions"><span class="comment-votes">${votesHTML("comment", comment)}</span><button class="secondary small" data-action="reply" data-post="${post.id}" data-comment="${comment.id}" data-nickname="${escapeHtml(comment.nickname)}">Rispondi</button></div>
    </div>`;
  }).join("") || '<div class="hint">Ancora nessun commento.</div>';
}
function postHTML(post) {
  return `<article class="card post" data-post="${post.id}">
    <div class="meta"><span class="nickname">@${escapeHtml(post.nickname)}</span><span>${post.flair ? `<span class="flair">${escapeHtml(post.flair)}</span> · ` : ""}${formatDate(post.created_at)}</span></div>
    <div class="body">${deskEmojis.renderBody(post.body, post.entities)}</div>${mediaHTML(post.media)}
    <div class="post-poll">${pollHTML(post)}</div>
    <div class="actions post-votes">${votesHTML("post", post)}</div>
    <details class="comments" data-post="${post.id}"><summary>${post.comments_count ? `${post.comments_count} commenti` : "Scrivi un commento"}</summary>
      <div class="comment-list"></div>
      <div class="reply-banner" id="reply-${post.id}"><span></span><button class="secondary small" data-action="cancel-reply" data-post="${post.id}">Annulla</button></div>
      <form class="comment-form" data-post="${post.id}"><input required aria-label="Testo del commento" placeholder="Scrivi un commento…"><button type="button" class="secondary" data-emoji-picker aria-label="Inserisci emoji Axoo">Emoji</button><button class="primary" type="submit">Invia</button></form>
    </details></article>`;
}
function updateFeedCount() { document.querySelector("#feed-count").textContent = state.actorId && state.data?.posts.length ? `${state.data.posts.length} post recenti` : ""; }
function renderFeed() {
  const open = new Set([...feed.querySelectorAll("details[open]")].map(d => d.dataset.post));
  updateFeedCount();
  feed.innerHTML = !state.actorId ? '<div class="card empty">Crea un profilo per iniziare.</div>' : !state.data.posts.length ? '<div class="card empty">Il feed è vuoto. Pubblica il primo post.</div>' : state.data.posts.map(postHTML).join("");
  for (const form of feed.querySelectorAll(".comment-form")) {
    const draft = readDraft(form.dataset.post); form.querySelector("input").value = draft.body || "";
    if (draft.reply_to_comment_id) state.reply.set(form.dataset.post, draft.reply_to_comment_id);
    updateReplyBanner(form.dataset.post);
  }
  for (const details of feed.querySelectorAll(".comments")) if (open.has(details.dataset.post)) details.open = true;
}
function postNode(id) { return feed.querySelector(`article[data-post="${CSS.escape(id)}"]`); }
function updateReplyBanner(id) {
  const banner = postNode(id)?.querySelector(".reply-banner"); if (!banner) return;
  const target = state.reply.get(id), parent = state.data.posts.find(p => p.id === id)?.comments?.find(c => c.id === target);
  banner.classList.toggle("visible", Boolean(target)); banner.querySelector("span").textContent = parent ? `Risposta a @${parent.nickname}` : "Risposta al commento";
}
async function loadComments(post, details) {
  if (post.commentsLoaded) { details.querySelector(".comment-list").innerHTML = commentsHTML(post); return; }
  if (post.commentsLoading) return;
  post.commentsLoading = true; details.querySelector(".comment-list").innerHTML = '<p class="hint">Caricamento…</p>';
  const author = state.actorId;
  try {
    const rows = await api(`/api/posts/${encodeURIComponent(post.id)}/comments?actor=${encodeURIComponent(author)}`);
    if (state.actorId !== author || !details.isConnected) return;
    const saved = new Set((rows || []).map(c => c.id));
    post.comments = [...(rows || []), ...(post.comments || []).filter(c => !saved.has(c.id))]; post.commentsLoaded = true;
    details.querySelector(".comment-list").innerHTML = commentsHTML(post); updateReplyBanner(post.id);
  } catch (error) {
    if (details.isConnected) details.querySelector(".comment-list").innerHTML = '<p class="hint">Impossibile caricare. Chiudi e riapri per riprovare.</p>';
    throw error;
  } finally { post.commentsLoading = false; }
}
feed.addEventListener("toggle", event => {
  const details = event.target; if (!details.matches("details.comments") || !details.open) return;
  const post = state.data?.posts.find(p => p.id === details.dataset.post); if (post) guarded(() => loadComments(post, details));
}, true);
actorSearch.addEventListener("input", renderActorOptions);
actorSearch.addEventListener("keydown", event => {
  if (event.key === "Enter" && state.matches.length) { event.preventDefault(); actor.value = state.matches[0].id; actor.dispatchEvent(new Event("change")); }
  if (event.key === "Escape") { actorSearch.value = ""; renderActorOptions(); actorSearch.blur(); }
});
document.addEventListener("keydown", event => { if (event.key === "/" && !event.ctrlKey && !event.metaKey && !event.altKey && !event.target.closest("input,textarea,select,[contenteditable=true]")) { event.preventDefault(); actorSearch.focus(); } });
actor.addEventListener("change", () => guarded(async () => {
  const previous = state.actorId; state.actorId = actor.value; if (!state.actorId || previous === state.actorId) return;
  localStorage.setItem("axoo.contentdesk.actor", state.actorId); actorSearch.value = ""; renderActorOptions(); state.reply.clear();
  await load();
}));
document.querySelector("#refresh").addEventListener("click", () => guarded(() => load({profiles:true})));
morePosts.addEventListener("click", () => guarded(() => load({append:true})));
const postForm = document.querySelector("#post-form");
const pollCount = document.querySelector("#poll-count");
const pollFields = [...postForm.querySelectorAll("[data-poll-option]")];
function updatePollEditor() {
  const count = Number(pollCount.value);
  pollFields.forEach((field, index) => { field.hidden = index >= count; field.querySelector("input").required = index < count; });
  document.querySelector("#poll-note").hidden = !count;
}
pollCount.value = readDraft().poll?.options?.length || "";
pollFields.forEach((field, index) => { field.querySelector("input").value = readDraft().poll?.options?.[index]?.text || ""; });
pollCount.addEventListener("change", updatePollEditor);
updatePollEditor();
function savePostDraft() {
  const old = readDraft(); writeDraft(null, {...old, client_id:old.client_id || crypto.randomUUID(), body:document.querySelector("#post-body").value, flair:document.querySelector("#post-flair").value, media_url:document.querySelector("#media-url").value, poll:pollCount.value ? {options:pollFields.slice(0, Number(pollCount.value)).map(field => ({text:field.querySelector("input").value}))} : null});
}
postForm.addEventListener("input", savePostDraft); postForm.addEventListener("change", savePostDraft);
for (const [key, id] of [["body","post-body"], ["flair","post-flair"], ["media_url","media-url"]]) document.querySelector(`#${id}`).value = readDraft()[key] || "";
postForm.addEventListener("submit", event => {
  event.preventDefault(); const button = event.submitter || postForm.querySelector('[type="submit"]'); if (button.disabled) return;
  guarded(async () => {
    savePostDraft(); postForm.querySelectorAll("input,textarea,select,button").forEach(control => control.disabled = true); actor.disabled = true; actorSearch.disabled = true;
    try {
      const post = await api("/api/posts", {method:"POST", body:JSON.stringify({...readDraft(), actor_id:state.actorId})});
      postForm.reset(); updatePollEditor(); localStorage.removeItem(draftKey());
      state.data.posts = [{...post, comments:[], commentsLoaded:true}, ...state.data.posts.filter(p => p.id !== post.id)];
      renderFeed(); notice("Post pubblicato");
    } finally { postForm.querySelectorAll("input,textarea,select,button").forEach(control => control.disabled = false); actor.disabled = false; actorSearch.disabled = false; }
  });
});
document.querySelector("#profile-form").addEventListener("submit", event => {
  event.preventDefault(); const button = event.submitter; if (button.disabled) return;
  guarded(async () => {
    button.disabled = true;
    try {
      const created = await api("/api/profiles", {method:"POST", body:JSON.stringify({nickname:document.querySelector("#nickname").value, gender:document.querySelector("#profile-gender").value, university_id:document.querySelector("#university").value})});
      state.actorId = created.id; localStorage.setItem("axoo.contentdesk.actor", state.actorId); event.target.reset(); notice("Profilo creato"); await load({profiles:true});
    } finally { button.disabled = false; }
  });
});
document.querySelector("#bulk-profile-form").addEventListener("submit", event => {
  event.preventDefault(); const form = event.target, button = form.querySelector('[type="submit"]');
  if (button.disabled) return;
  const payload = {count:Number(document.querySelector("#profile-count").value), gender:document.querySelector("#bulk-profile-gender").value, university_id:document.querySelector("#bulk-university").value};
  guarded(async () => {
    form.querySelectorAll("input,select,button").forEach(control => control.disabled = true);
    try {
      const created = await api("/api/profiles/bulk", {method:"POST", body:JSON.stringify(payload)});
      notice(`${created.length} profili di prova creati`);
      await load({profiles:true});
    } finally { form.querySelectorAll("input,select,button").forEach(control => control.disabled = false); }
  });
});
feed.addEventListener("input", event => {
  const form = event.target.closest(".comment-form"); if (!form) return;
  const old = readDraft(form.dataset.post);
  writeDraft(form.dataset.post, {...old, client_id:old.client_id || crypto.randomUUID(), body:form.querySelector("input").value, reply_to_comment_id:state.reply.get(form.dataset.post) || ""});
});
feed.addEventListener("click", event => guarded(async () => {
  const button = event.target.closest("button[data-action]"); if (!button || button.disabled) return;
  const {action, post:id} = button.dataset;
  if (action === "reply" || action === "cancel-reply") {
    if (action === "reply") state.reply.set(id, button.dataset.comment); else state.reply.delete(id);
    const form = postNode(id).querySelector(".comment-form");
    writeDraft(id, {...readDraft(id), body:form.querySelector("input").value, reply_to_comment_id:state.reply.get(id) || ""});
    updateReplyBanner(id); form.querySelector("input").focus(); return;
  }
  if (action === "poll-vote") {
    const post = state.data.posts.find(p => p.id === id), author = state.actorId;
    if (!post?.poll || post.poll.my_option_id || post.pollSending) return;
    const container = button.closest(".post-poll");
    post.pollSending = true; container.querySelectorAll("button").forEach(control => control.disabled = true);
    try {
      const poll = await api("/api/posts/poll/vote", {method:"PUT", body:JSON.stringify({actor_id:author, post_id:id, option_id:button.dataset.option})});
      if (author !== state.actorId || !container.isConnected) return;
      post.poll = poll; container.innerHTML = pollHTML(post);
    } catch (error) {
      if (author === state.actorId) await load().catch(() => {});
      throw error;
    } finally {
      post.pollSending = false;
      if (author === state.actorId && container.isConnected) container.innerHTML = pollHTML(post);
    }
    return;
  }
  if (action === "vote") {
    button.disabled = true;
    try {
      const author = state.actorId, kind = button.dataset.kind, targetKey = kind === "post" ? "post_id" : "comment_id";
      const result = await api(`/api/${kind === "post" ? "posts" : "comments"}/vote`, {method:"PUT", body:JSON.stringify({actor_id:state.actorId, [targetKey]:button.dataset.id, value:Number(button.dataset.value)})});
      if (author !== state.actorId || !button.isConnected) return;
      const post = state.data.posts.find(p => kind === "post" ? p.id === button.dataset.id : p.comments?.some(c => c.id === button.dataset.id));
      const item = kind === "post" ? post : post?.comments.find(c => c.id === button.dataset.id);
      if (item) { Object.assign(item, result); button.closest(kind === "post" ? ".post-votes" : ".comment-votes").innerHTML = votesHTML(kind, item); }
    } finally { button.disabled = false; }
  }
}));
feed.addEventListener("submit", event => {
  const form = event.target.closest("form.comment-form"); if (!form) return; event.preventDefault();
  const button = form.querySelector('[type="submit"]'); if (button.disabled) return;
  guarded(async () => {
    const id = form.dataset.post, input = form.querySelector("input"), old = readDraft(id);
    const draft = {...old, client_id:old.client_id || crypto.randomUUID(), body:input.value, reply_to_comment_id:state.reply.get(id) || ""}; writeDraft(id, draft);
    button.disabled = true; input.disabled = true; actor.disabled = true; actorSearch.disabled = true;
    try {
      const comment = await api("/api/comments", {method:"POST", body:JSON.stringify({...draft, actor_id:state.actorId, post_id:id})});
      const post = state.data.posts.find(p => p.id === id); if (!post) return;
      if (!post.comments) post.comments = [];
      if (!post.comments.some(c => c.id === comment.id)) { post.comments.push(comment); post.comments_count = (post.comments_count || 0) + 1; }
      state.reply.delete(id); localStorage.removeItem(draftKey(id)); input.value = "";
      const node = postNode(id); node.querySelector(".comment-list").innerHTML = commentsHTML(post); node.querySelector("summary").textContent = `${post.comments_count} commenti`; updateReplyBanner(id); notice("Commento pubblicato");
    } finally { button.disabled = false; input.disabled = false; actor.disabled = false; actorSearch.disabled = false; input.focus(); }
  });
});
async function guarded(operation) { try { await operation(); } catch (error) { if (error.name !== "AbortError") notice(error.message || "Operazione non riuscita"); } }
guarded(async () => {
  await Promise.all([
    deskEmojis.load().catch(() => notice("Catalogo emoji non disponibile: controlla la cartella Axooji.")),
    load(),
  ]);
});
