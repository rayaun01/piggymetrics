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

  async function renderDiagram(diagram) {
    if (!window.mermaid || diagram.dataset.mermaidRendered === "true") return;
    diagram.textContent = diagram.dataset.source || "";
    try {
      await window.mermaid.run({ nodes: [diagram] });
      diagram.dataset.mermaidRendered = "true";
    } catch (error) {
      diagram.classList.add("diagram-error");
      diagram.textContent = "Diagram unavailable — use “Show source” to inspect it.";
    }
  }

  function isInsideClosedToggle(diagram) {
    return Boolean(diagram.closest("details.toggle-card:not([open])"));
  }

  async function renderDiagrams(diagrams) {
    for (const diagram of diagrams) {
      if (!isInsideClosedToggle(diagram)) await renderDiagram(diagram);
    }
  }

  if (window.mermaid) {
    window.mermaid.initialize({ startOnLoad: false, theme: "neutral", securityLevel: "strict" });
    renderDiagrams([...document.querySelectorAll(".mermaid-diagram")]);
    document.querySelectorAll("details.toggle-card").forEach((toggle) => {
      toggle.addEventListener("toggle", () => {
        if (toggle.open) {
          renderDiagrams([...toggle.querySelectorAll(".mermaid-diagram")]);
        }
      });
    });
  }
})();
