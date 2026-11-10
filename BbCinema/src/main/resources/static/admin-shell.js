"use strict";

(() => {
  const page = document.body.dataset.adminPage;
  if (!page) return;
  const icons = {
    dashboard: "M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z",
    movie: "M4 3h16v18H4zM4 8h16M4 16h16M8 3v5M16 3v5M8 16v5M16 16v5",
    genre: "M3 5h8l10 10-6 6L3 9V5zM7 8h.01",
    cinema: "M3 21V7l9-4 9 4v14M3 12h18M8 21v-5h8v5M7 8h.01M12 7h.01M17 8h.01",
    province: "M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 1116 0zM15 10a3 3 0 11-6 0 3 3 0 016 0z",
    room: "M3 4h18v15H3zM3 8h18M8 19v3M16 19v3",
    seats: "M5 13V7a3 3 0 013-3h8a3 3 0 013 3v6M3 12h4v5h10v-5h4v8H3zM6 20v2M18 20v2",
    types: "M4 5h16v14H4zM8 5v14M16 5v14M4 12h16",
    clock: "M21 12a9 9 0 11-18 0 9 9 0 0118 0zM12 7v5l3 2",
    price: "M3 6h18v5a2 2 0 000 4v4H3v-4a2 2 0 000-4V6zM16 6v2M16 11v2M16 16v3",
    api: "M8 7l-5 5 5 5M16 7l5 5-5 5M14 4l-4 16",
    logout: "M9 3H3v18h6M14 8l5 4-5 4M7 12h12"
  };
  const titles = { dashboard: "Tổng quan", movies: "Phim và thể loại", cinema: "Hệ thống rạp", showtimes: "Lịch chiếu và giá vé" };
  const menu = [
    ["dashboard", "Tổng quan", "/admin.html", "dashboard"],
    ["movie", "Phim", "/movie-management.html", "movies"],
    ["genre", "Thể loại", "/movie-management.html#genre-panel", "genres"],
    ["cinema", "Rạp chiếu", "/cinema-management.html#cinemas", "cinemas"],
    ["province", "Tỉnh / thành", "/cinema-management.html#provinces", "provinces"],
    ["room", "Phòng chiếu", "/cinema-management.html#auditoriums", "auditoriums"],
    ["types", "Loại ghế", "/cinema-management.html#seat-types", "seat-types"],
    ["seats", "Sơ đồ ghế", "/cinema-management.html#seat-map", "seat-map"],
    ["clock", "Suất chiếu", "/showtime-management.html", "showtimes"],
    ["price", "Giá vé", "/showtime-management.html#price-editor", "prices"]
  ];
  function make(tag, className, text) { const node = document.createElement(tag); if (className) node.className = className; if (text !== undefined) node.textContent = text; return node; }
  function icon(name) { const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg"); svg.setAttribute("viewBox", "0 0 24 24"); svg.setAttribute("aria-hidden", "true"); const path = document.createElementNS(svg.namespaceURI, "path"); path.setAttribute("d", icons[name] || icons.dashboard); svg.append(path); return svg; }
  function link(name, text, href, key) { const node = make("a", "admin-nav-link"); node.href = href; if (key) node.dataset.menuKey = key; node.append(icon(name), make("span", "", text)); return node; }
  function closeMenu() { document.body.classList.remove("admin-menu-open"); document.getElementById("admin-menu-toggle")?.setAttribute("aria-expanded", "false"); }
  const sidebarScrollKey = "bbcinema.admin.sidebarScroll";
  let sessionEnded = false;
  function rememberSidebarScroll(sidebar) {
    if (sessionEnded) return;
    try { window.sessionStorage.setItem(sidebarScrollKey, String(sidebar.scrollTop)); }
    catch (_) { /* Navigation also works when browser storage is unavailable. */ }
  }
  function restoreSidebarScroll(sidebar) {
    try {
      const saved = Number(window.sessionStorage.getItem(sidebarScrollKey));
      if (Number.isFinite(saved) && saved >= 0) sidebar.scrollTop = saved;
    } catch (_) { /* Keep the default scroll position when storage is unavailable. */ }
  }
  function currentKey() { const hash = location.hash.slice(1); if (page === "cinema") return ["provinces", "auditoriums", "seat-types", "seat-map"].includes(hash) ? hash : "cinemas"; if (page === "movies") return hash === "genre-panel" ? "genres" : "movies"; if (page === "showtimes") return hash === "price-editor" ? "prices" : "showtimes"; return "dashboard"; }
  function applySection(scroll = false) {
    const key = currentKey();
    document.querySelectorAll("[data-menu-key]").forEach((item) => { const selected = item.dataset.menuKey === key; item.classList.toggle("active", selected); if (selected) item.setAttribute("aria-current", "page"); else item.removeAttribute("aria-current"); });
    const item = menu.find((entry) => entry[3] === key); const title = item?.[1] || titles[page];
    const heading = document.getElementById("admin-page-title"); if (heading) heading.textContent = title;
    document.title = `${title} · Bbcinema Admin`;
    if (page === "cinema") {
      document.querySelectorAll("[data-workspace-section]").forEach((node) => { const visible = node.dataset.workspaceSection.split(" ").includes(key); node.hidden = !visible; if (visible && node.tagName === "DETAILS") node.open = true; });
      const selectors = document.querySelector("[data-cinema-selectors]"); if (selectors) selectors.hidden = ["provinces", "seat-types"].includes(key);
    }
    if (page === "movies") {
      document.querySelectorAll("[data-workspace-section]").forEach((node) => { node.hidden = node.dataset.workspaceSection !== key; });
      document.querySelector(".management-layout")?.classList.toggle("genre-mode", key === "genres");
    }
    if (scroll && page === "showtimes" && key === "prices") document.getElementById("price-editor")?.scrollIntoView({ behavior: "smooth", block: "center" });
    closeMenu();
  }
  async function logout(button) {
    button.disabled = true;
    try {
      const csrfResponse = await fetch("/api/auth/csrf", { credentials: "same-origin", headers: { Accept: "application/json" } });
      if (!csrfResponse.ok) throw new Error("Không lấy được phiên đăng xuất.");
      const csrf = await csrfResponse.json();
      const response = await fetch("/api/auth/logout", { method: "POST", credentials: "same-origin", headers: { [csrf.headerName]: csrf.token } });
      if (!response.ok) throw new Error("Chưa thể đăng xuất. Vui lòng thử lại.");
      sessionEnded = true;
      try { window.sessionStorage.removeItem(sidebarScrollKey); } catch (_) { }
      location.assign("/login");
    } catch (error) { const message = document.getElementById("admin-shell-message"); message.textContent = error.message; message.hidden = false; button.disabled = false; }
  }
  function build(user) {
    const aside = make("aside", "admin-sidebar"); aside.id = "admin-sidebar";
    const brand = make("a", "admin-brand"); brand.href = "/admin.html"; brand.append(make("span", "admin-brand-mark", "B"), make("span", "", "Bbcinema."));
    aside.append(brand, make("p", "admin-sidebar-caption", "CINEMA MANAGEMENT"));
    const nav = make("nav", "admin-navigation"); nav.setAttribute("aria-label", "Chức năng quản trị");
    menu.forEach((entry, index) => { if ([0, 1, 3, 8].includes(index)) nav.append(make("p", "admin-nav-group", { 0: "KHÔNG GIAN LÀM VIỆC", 1: "NỘI DUNG", 3: "HỆ THỐNG RẠP", 8: "KINH DOANH" }[index])); nav.append(link(...entry)); });
    aside.append(nav);
    const tools = make("div", "admin-sidebar-tools"); tools.append(link("api", "Swagger API", "/swagger-ui/index.html"));
    const exit = make("button", "admin-nav-link admin-signout"); exit.id = "admin-logout"; exit.type = "button"; exit.append(icon("logout"), make("span", "", "Đăng xuất")); exit.addEventListener("click", () => void logout(exit)); tools.append(exit);
    aside.append(tools, make("p", "admin-sidebar-footer", "Made for the love of cinema."));
    const header = make("header", "admin-header");
    const toggle = make("button", "admin-menu-toggle", "☰"); toggle.id = "admin-menu-toggle"; toggle.type = "button"; toggle.setAttribute("aria-label", "Mở menu quản trị"); toggle.setAttribute("aria-controls", "admin-sidebar"); toggle.setAttribute("aria-expanded", "false"); toggle.addEventListener("click", () => { const open = document.body.classList.toggle("admin-menu-open"); toggle.setAttribute("aria-expanded", String(open)); });
    const heading = make("div", "admin-header-heading"); heading.append(make("p", "", "Bbcinema / Quản trị")); const title = make("h1", "", titles[page]); title.id = "admin-page-title"; heading.append(title);
    const profile = make("div", "admin-profile"); const initials = (user.fullName || "Admin").trim().split(/\s+/).slice(-2).map((s) => s[0]).join("").toUpperCase(); profile.append(make("span", "admin-avatar", initials)); const info = make("div"); info.append(make("strong", "", user.fullName || "Quản trị viên"), make("small", "", "Administrator")); profile.append(info);
    header.append(toggle, heading, profile);
    const backdrop = make("button", "admin-menu-backdrop"); backdrop.type = "button"; backdrop.setAttribute("aria-label", "Đóng menu"); backdrop.addEventListener("click", closeMenu);
    const message = make("div", "admin-message admin-shell-message"); message.id = "admin-shell-message"; message.hidden = true; message.setAttribute("role", "alert");
    document.body.prepend(aside, backdrop, header, message);
    document.body.classList.add("admin-ready"); document.body.dataset.adminRole = "ADMIN";
    applySection(); window.addEventListener("hashchange", () => applySection(true)); document.addEventListener("keydown", (event) => { if (event.key === "Escape") closeMenu(); });
    restoreSidebarScroll(aside);
    aside.addEventListener("scroll", () => rememberSidebarScroll(aside), { passive: true });
    window.addEventListener("pagehide", () => rememberSidebarScroll(aside));
    document.querySelectorAll(".admin-nav-link").forEach((node) => node.addEventListener("click", () => {
      rememberSidebarScroll(aside);
      if (new URL(node.href || location.href, location.origin).pathname === location.pathname) setTimeout(() => applySection(true), 0);
    }));
    document.dispatchEvent(new CustomEvent("bbcinema:admin-ready", { detail: user }));
    return user;
  }
  function denied() { document.body.classList.add("admin-denied"); const panel = make("section", "admin-access-panel"); panel.append(make("p", "admin-eyebrow", "BBCINEMA"), make("h1", "", "Bạn chưa có quyền quản trị."), make("p", "", "Trang này dành cho tài khoản Admin.")); const login = make("a", "admin-access-link", "Đăng nhập tài khoản khác"); login.href = "/login"; panel.append(login); document.body.append(panel); }
  window.bbcinemaAdminReady = (async () => {
    try {
      const response = await fetch("/api/users/me", { credentials: "same-origin", headers: { Accept: "application/json" } });
      if (response.status === 401) { location.replace("/login"); return null; }
      if (!response.ok) throw new Error("Không thể kiểm tra phiên quản trị. Vui lòng tải lại trang.");
      const user = await response.json();
      if (user.role !== "ADMIN") { if (page === "cinema") { document.body.classList.remove("admin-workspace"); return null; } denied(); return null; }
      return build(user);
    } catch (error) { document.body.classList.add("admin-denied"); const panel = make("section", "admin-access-panel"); panel.append(make("h1", "", "Chưa thể kết nối Bbcinema"), make("p", "", error.message)); const retry = make("button", "admin-primary", "Tải lại trang"); retry.addEventListener("click", () => location.reload()); panel.append(retry); document.body.append(panel); return null; }
  })();
})();
