// A single picker shared by post and comment fields. The server expands tokens.
const deskEmojis = (() => {
  let catalog = null;
  let byId = new Map();
  let target = null;
  let selectionStart = 0;
  let selectionEnd = 0;
  const dialog = document.querySelector("#emoji-picker");
  const pack = document.querySelector("#emoji-pack");
  const search = document.querySelector("#emoji-search");
  const grid = document.querySelector("#emoji-grid");
  const previewURL = name => `/api/emoji-preview?name=${encodeURIComponent(name)}`;

  async function load() {
    catalog = await api("/api/emojis");
    byId = new Map(catalog.emojis.map(emoji => [emoji.id, emoji]));
    pack.replaceChildren(new Option("Tutti i pack", ""));
    catalog.categories.forEach(category => pack.add(new Option(category.label, category.id)));
  }

  function renderPicker() {
    const query = search.value.trim().toLowerCase();
    const names = pack.value ? new Set(catalog.categories.find(c => c.id === pack.value)?.items) : null;
    const labels = new Map(catalog.categories.map(c => [c.id, c.label.toLowerCase()]));
    const emojis = catalog.emojis.filter(emoji => !emoji.pickerHidden && (!names || names.has(emoji.name)) &&
      `${emoji.name} ${emoji.fallback} ${labels.get(emoji.name.split('/')[0]) || ''}`.includes(query));
    grid.replaceChildren();
    for (const emoji of emojis) {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "emoji-choice";
      button.title = emoji.name;
      button.setAttribute("aria-label", emoji.name);
      const image = document.createElement("img");
      image.src = previewURL(emoji.name);
      image.alt = emoji.fallback;
      image.loading = "lazy";
      button.append(image);
      button.addEventListener("click", () => {
        if (!target?.isConnected) { dialog.close(); return; }
        target.setRangeText(`:${emoji.name}:`, selectionStart, selectionEnd, "end");
        target.dispatchEvent(new Event("input", {bubbles: true}));
        dialog.close();
        target.focus();
      });
      grid.append(button);
    }
    if (!emojis.length) grid.textContent = "Nessuna emoji trovata.";
  }

  document.addEventListener("click", event => {
    const button = event.target.closest("button[data-emoji-picker]");
    if (!button) return;
    if (!catalog) { notice("Catalogo emoji non disponibile. Ricarica la pagina."); return; }
    target = button.closest(".field")?.querySelector("textarea, input") || button.closest("form").querySelector("textarea, input");
    selectionStart = target.selectionStart;
    selectionEnd = target.selectionEnd;
    renderPicker();
    dialog.showModal();
  });
  document.querySelector("#emoji-close").addEventListener("click", () => dialog.close());
  pack.addEventListener("change", renderPicker);
  search.addEventListener("input", renderPicker);

  function renderBody(body = "", entities = []) {
    const points = Array.from(body); // API uses code points, including multi-code-point fallbacks.
    let position = 0;
    let html = "";
    for (const entity of [...(entities || [])].sort((a, b) => a.offset - b.offset)) {
      const emoji = byId.get(entity.custom_emoji_id);
      if (entity.type !== "custom_emoji" || !emoji || !Number.isInteger(entity.offset) ||
          !Number.isInteger(entity.length) || entity.offset < position || entity.length < 1 ||
          entity.offset + entity.length > points.length) continue;
      html += escapeHtml(points.slice(position, entity.offset).join(""));
      const fallback = points.slice(entity.offset, entity.offset + entity.length).join("");
      html += `<img class="emoji-inline" src="${previewURL(emoji.name)}" alt="${escapeHtml(fallback)}" title="${escapeHtml(emoji.name)}" loading="lazy">`;
      position = entity.offset + entity.length;
    }
    return html + escapeHtml(points.slice(position).join(""));
  }

  return {load, renderBody};
})();
