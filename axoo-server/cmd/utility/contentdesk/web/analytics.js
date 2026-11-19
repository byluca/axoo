const analyticsState = { request: null, campuses: [] };
const analyticsDays = document.querySelector("#analytics-days");
const analyticsCampus = document.querySelector("#analytics-campus");
const analyticsStatus = document.querySelector("#analytics-status");
const analyticsData = document.querySelector("#analytics-data");
const analyticsNumber = new Intl.NumberFormat("it-IT", { maximumFractionDigits: 1 });
const metricNumber = value => analyticsNumber.format(value ?? 0);

function showDeskSection(section) {
  for (const name of ["content", "analytics"]) {
    document.querySelector(`#${name}-panel`).hidden = name !== section;
    document.querySelector(`#${name}-tab`).setAttribute("aria-pressed", String(name === section));
  }
  if (section === "analytics") loadAnalytics();
}

async function loadAnalytics() {
  analyticsState.request?.abort();
  const controller = new AbortController();
  analyticsState.request = controller;
  analyticsStatus.textContent = "Caricamento statistiche…";
  analyticsStatus.classList.remove("analytics-error");
  analyticsData.replaceChildren();
  analyticsData.setAttribute("aria-busy", "true");
  const query = new URLSearchParams({ days: analyticsDays.value });
  if (analyticsCampus.value) query.set("university_id", analyticsCampus.value);
  try {
    const overview = await api(`/api/analytics?${query}`, { signal: controller.signal });
    if (controller.signal.aborted) return;
    if (!analyticsCampus.value) {
      analyticsState.campuses = overview.campuses || [];
      analyticsCampus.innerHTML = '<option value="">Tutti i campus</option>' + analyticsState.campuses.map(campus =>
        `<option value="${escapeHtml(campus.university_id)}">${escapeHtml(campus.university_name)}</option>`).join("");
    }
    renderAnalytics(overview);
    const generated = new Intl.DateTimeFormat("it-IT", { dateStyle: "short", timeStyle: "short", timeZone: "Europe/Rome" }).format(new Date(overview.generated_at));
    analyticsStatus.textContent = `Aggiornato ${generated}`;
  } catch (error) {
    if (controller.signal.aborted) return;
    analyticsStatus.textContent = `Statistiche non disponibili: ${error.message}. Puoi riprovare con “Aggiorna statistiche”.`;
    analyticsStatus.classList.add("analytics-error");
  } finally {
    if (analyticsState.request === controller) analyticsData.setAttribute("aria-busy", "false");
  }
}

function analyticsDate(value) {
  return new Intl.DateTimeFormat("it-IT", { day: "2-digit", month: "2-digit", timeZone: "Europe/Rome" }).format(new Date(`${value}T12:00:00Z`));
}

function renderAnalytics(overview) {
  const s = overview.summary;
  const referrals = overview.referrals || {};
  const referralMetrics = [
    ["Utenti con almeno un invito valido", referrals.total_inviters ?? 0, `${metricNumber(referrals.period_inviters)} con inviti confermati nel periodo`],
    ["Iscritti tramite invito", referrals.total_invitees ?? 0, `${metricNumber(referrals.period_invitees)} codici confermati nel periodo`],
    ["Utenti con emoji sbloccate", referrals.total_unlocked_users ?? 0, `${metricNumber(referrals.period_unlocked_users)} sblocchi nel periodo · Amici di Axoo`],
  ];
  const metrics = [
    ["Utenti registrati", s.total_users, "Totale attuale"],
    ["Nuovi utenti", s.new_users, "Registrati nel periodo"],
    ["Utenti attivi", s.active_users, "Aperture o attività nel periodo"],
    ["Utenti partecipanti", s.engaged_users, "Almeno un post, commento o voto"],
    ["Attivi oggi", s.dau, "DAU"],
    ["Attivi in 7 giorni", s.wau, "WAU"],
    ["Attivi in 30 giorni", s.mau, "MAU"],
    ["Post creati", s.posts_created, "Nel periodo"],
    ["Commenti creati", s.comments_created, "Nel periodo"],
    ["Voti modificati", s.votes_changed, "Inclusi cambi e rimozioni del voto"],
    ["Azioni totali", s.total_actions, "Post + commenti + voti"],
    ["Partecipazione", `${metricNumber(s.participation_rate)}%`, "Partecipanti / utenti attivi"],
    ["Azioni per partecipante", s.actions_per_engaged, "Media nel periodo"],
    ["Rapporto DAU / MAU", `${metricNumber(s.dau_mau)}%`, "Attivi oggi / attivi in 30 giorni"],
  ];
  const daily = overview.daily || [];
  const audienceMetrics = [
    ["Già registrati tornati oggi", s.returning_today, "Attivi oggi, iscritti prima di oggi · finestra fissa"],
    ["Già registrati tornati nel periodo", s.returning_users, "Attivi, iscritti prima dell’inizio del periodo"],
    ["Nuovi iscritti attivi", s.new_active_users, "Iscritti e attivi nel periodo"],
    ["Utenti già registrati", s.previous_users, "Iscritti prima dell’inizio del periodo"],
    ["Utenti inattivi", s.inactive_users, "Nessuna attività nel periodo"],
    ["Attivi senza partecipazione", s.passive_users, "Nessun post, commento o voto nel periodo"],
    ["Attivi in almeno 2 giorni", s.repeat_users, "Utenti unici nel periodo · con Oggi vale 0"],
    ["Giorni attivi per utente", s.active_days_per_user, "Media tra gli attivi nel periodo"],
    ["Quota di utenti attivi", `${metricNumber(s.active_rate)}%`, "Attivi / totale registrati"],
    ["Nuovi iscritti attivati", `${metricNumber(s.activation_rate)}%`, "Nuovi attivi / nuovi iscritti"],
    ["Ritorno dei già registrati", `${metricNumber(s.return_rate)}%`, "Già registrati attivi / iscritti prima del periodo"],
  ];
  const campuses = overview.campuses || [];
  const maxActive = Math.max(1, ...daily.map(day => day.active_users));
  const formattedMetric = value => typeof value === "number" ? metricNumber(value) : value;
  const metricRows = rows => rows.map(([label, value, note]) =>
    `<div class="metric-row"><div><span class="metric-label">${label}</span><span class="metric-note">${note}</span></div><span class="metric-value">${formattedMetric(value)}</span></div>`).join("");
  analyticsData.innerHTML = `<div class="metric-grid">${metrics.slice(0, 4).map(([label, value, note], index) =>
    `<article class="metric ${index === 2 ? "metric-emphasis" : ""}"><div class="metric-label">${label}</div><div class="metric-value">${formattedMetric(value)}</div><div class="metric-note">${note}</div></article>`).join("")}</div>
    ${s.active_users === 0 ? '<div class="card empty">Nessuna apertura o attività registrata nel periodo per il campus selezionato.</div>' : ""}
    <section class="card" id="referral-metrics"><h2 class="section-title">Inviti ed emoji</h2><p class="hint">Totali degli utenti reali, con il dettaglio del periodo selezionato.</p>${metricRows(referralMetrics)}</section>
    <section class="card"><h2 class="section-title">Nuovi utenti e ritorni</h2>${metricRows(audienceMetrics)}</section>
    <section class="card chart-card"><div class="chart-heading"><h2 class="section-title">Utenti attivi per giorno</h2><span class="hint">Utenti unici · ${overview.period_days === 1 ? "oggi" : `${overview.period_days} giorni, oggi incluso`}</span></div>
      <div class="reach-metrics">${metrics.slice(4, 7).map(([label, value, note]) => `<div><span class="metric-label">${label} <span class="metric-note">${note}</span></span><span class="metric-value">${formattedMetric(value)}</span></div>`).join("")}</div>
      <div class="table-scroll"><div class="activity-chart" role="img" aria-label="Andamento giornaliero degli utenti attivi; valori disponibili nella tabella di dettaglio">${daily.map(day =>
        `<div class="activity-day" title="${analyticsDate(day.date)}: ${metricNumber(day.active_users)} utenti attivi"><span>${metricNumber(day.active_users)}</span><div class="activity-bar-track"><div class="activity-bar" style="height:${Math.round(day.active_users / maxActive * 100)}%"></div></div><span>${analyticsDate(day.date)}</span></div>`).join("")}</div></div>
    </section>
    <div class="analytics-groups"><section class="card"><h2 class="section-title">Attività nel periodo</h2>${metricRows(metrics.slice(7, 11))}</section><section class="card"><h2 class="section-title">Partecipazione</h2>${metricRows(metrics.slice(11))}</section></div>
    <section class="card campus-card"><div class="table-scroll"><table class="analytics-table"><caption>Campus <span class="hint">Nel periodo selezionato · tornati = iscritti prima del periodo</span></caption><thead><tr><th scope="col">Campus</th><th scope="col">Registrati</th><th scope="col">Attivi</th><th scope="col">Tornati</th><th scope="col">Partecipanti</th><th scope="col">Azioni</th></tr></thead><tbody>${campuses.map(campus =>
      `<tr><th scope="row">${escapeHtml(campus.university_name)}</th><td>${metricNumber(campus.total_users)}</td><td>${metricNumber(campus.active_users)}</td><td>${metricNumber(campus.returning_users)}</td><td>${metricNumber(campus.engaged_users)}</td><td>${metricNumber(campus.total_actions)}</td></tr>`).join("") || '<tr><td colspan="6">Nessun campus con utenti registrati.</td></tr>'}</tbody></table></div></section>
    <details class="card"><summary>Dettaglio giornaliero</summary><p class="hint">Nuovi attivi: iscritti quel giorno. Tornati: iscritti prima di quel giorno. Lo stesso utente può comparire in giorni diversi.</p><div class="table-scroll"><table class="analytics-table"><thead><tr><th scope="col">Giorno</th><th scope="col">Attivi</th><th scope="col">Nuovi attivi</th><th scope="col">Tornati</th><th scope="col">Partecipanti</th><th scope="col">Post</th><th scope="col">Commenti</th><th scope="col">Voti</th></tr></thead><tbody>${daily.map(day =>
      `<tr><th scope="row">${analyticsDate(day.date)}</th><td>${metricNumber(day.active_users)}</td><td>${metricNumber(day.new_active_users)}</td><td>${metricNumber(day.returning_users)}</td><td>${metricNumber(day.engaged_users)}</td><td>${metricNumber(day.posts_created)}</td><td>${metricNumber(day.comments_created)}</td><td>${metricNumber(day.votes_changed)}</td></tr>`).join("")}</tbody></table></div></details>`;
}

document.querySelector("#analytics-tab").addEventListener("click", () => showDeskSection("analytics"));
document.querySelector("#content-tab").addEventListener("click", () => showDeskSection("content"));
document.querySelector("#analytics-refresh").addEventListener("click", loadAnalytics);
analyticsDays.addEventListener("change", loadAnalytics);
analyticsCampus.addEventListener("change", loadAnalytics);
