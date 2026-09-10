(function () {
  "use strict";

  const body = document.body;
  const buttons = document.querySelectorAll(".view-button");
  const navToggle = document.querySelector(".nav-toggle");
  const sidebar = document.querySelector(".sidebar");

  buttons.forEach((button) => {
    button.addEventListener("click", () => {
      const view = button.dataset.view;
      body.classList.remove("view-en", "view-ja", "view-both");
      body.classList.add(`view-${view}`);
      buttons.forEach((item) => item.classList.toggle("is-active", item === button));
    });
  });

  if (navToggle && sidebar) {
    navToggle.addEventListener("click", () => {
      const open = sidebar.classList.toggle("is-open");
      navToggle.setAttribute("aria-expanded", String(open));
    });
    sidebar.querySelectorAll("a").forEach((link) => {
      link.addEventListener("click", () => {
        sidebar.classList.remove("is-open");
        navToggle.setAttribute("aria-expanded", "false");
      });
    });
  }

  const sections = [...document.querySelectorAll(".report-section")];
  const tocLinks = [...document.querySelectorAll(".toc-link")];
  if ("IntersectionObserver" in window && sections.length) {
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          tocLinks.forEach((link) => link.classList.toggle("is-active", link.dataset.section === entry.target.dataset.sectionId));
        });
      },
      { rootMargin: "-18% 0px -66% 0px", threshold: 0 }
    );
    sections.forEach((section) => observer.observe(section));
  }

  document.querySelectorAll(".source-toggle").forEach((toggle) => {
    toggle.addEventListener("click", () => {
      const block = toggle.closest(".visual-block");
      const source = block.querySelector(".source-code");
      const showing = !source.hidden;
      source.hidden = showing;
      toggle.textContent = showing ? "Show source" : "Hide source";
    });
  });

  async function renderDiagrams() {
    const diagrams = [...document.querySelectorAll(".mermaid-diagram")];
    if (!diagrams.length || !window.mermaid) return;
    window.mermaid.initialize({ startOnLoad: false, theme: "neutral", securityLevel: "strict" });
    for (const diagram of diagrams) {
      try {
        await window.mermaid.run({ nodes: [diagram] });
      } catch (error) {
        diagram.classList.add("diagram-error");
        diagram.textContent = "Diagram unavailable — use “Show source” to inspect it.";
      }
    }
  }

  renderDiagrams();
})();
