(() => {
  const key = "axoo.contentdesk.theme";
  const system = window.matchMedia("(prefers-color-scheme: dark)");
  let preference = "system";
  try {
    const saved = localStorage.getItem(key);
    if (["system", "light", "dark"].includes(saved)) preference = saved;
  } catch {}

  function apply() {
    document.documentElement.dataset.theme = preference === "system"
      ? (system.matches ? "dark" : "light") : preference;
  }
  apply();
  system.addEventListener("change", apply);
  document.addEventListener("DOMContentLoaded", () => {
    const selector = document.getElementById("desk-theme");
    selector.value = preference;
    selector.addEventListener("change", () => {
      preference = selector.value;
      try { localStorage.setItem(key, preference); } catch {}
      apply();
    });
  });
})();
