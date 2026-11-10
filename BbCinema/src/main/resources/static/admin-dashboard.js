"use strict";

(() => {
  if (document.body.dataset.adminPage !== "dashboard") return;
  const $ = (id) => document.getElementById(id);
  const money = new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" });
  const names = { NOW_SHOWING: "Đang chiếu", COMING_SOON: "Sắp chiếu", HIDDEN: "Ẩn", ENDED: "Đã kết thúc" };
  function make(tag, className, text) { const node = document.createElement(tag); if (className) node.className = className; if (text !== undefined) node.textContent = text; return node; }
  function dateTime(value) { const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}:\d{2})/.exec(value || ""); return match ? `${match[4]} · ${match[3]}/${match[2]}` : "Đang cập nhật"; }
  function posterUrl(value) { if (!value) return null; try { const local = /^\/demo\/posters\/[a-z0-9-]+\.svg$/.test(value) || /^\/uploads\/posters\/[a-f0-9]{32}\.(?:jpg|png)$/.test(value); const url = local ? new URL(value, location.origin) : new URL(value); return ["http:", "https:"].includes(url.protocol) && !url.username && !url.password ? url.href : null; } catch (_) { return null; } }
  function upcoming(rows) {
    $("upcoming-showtimes").replaceChildren();
    if (!rows.length) { const row = make("tr"); const cell = make("td", "admin-empty", "Chưa có suất đang mở bán. Tạo và mở bán một suất để bắt đầu."); cell.colSpan = 4; row.append(cell); $("upcoming-showtimes").append(row); return; }
    for (const showtime of rows) {
      const row = make("tr"), title = make("td"); title.append(make("strong", "", showtime.movieTitle), make("small", "", `${showtime.cinemaName} · ${showtime.auditoriumName}`));
      const amount = (showtime.prices || []).length ? money.format(Math.min(...showtime.prices.map((p) => Number(p.price)))) : "Chưa thiết lập";
      const action = make("td"), link = make("a", "", "↗"); link.href = "/showtime-management.html"; link.setAttribute("aria-label", `Quản lý suất ${showtime.movieTitle}`); action.append(link);
      row.append(title, make("td", "", dateTime(showtime.startTime)), make("td", "", amount), action); $("upcoming-showtimes").append(row);
    }
  }
  function movies(rows) {
    $("recent-movies").replaceChildren();
    if (!rows.length) { $("recent-movies").append(make("p", "admin-empty", "Danh mục đang trống. Thêm bộ phim đầu tiên ở mục Phim.")); return; }
    for (const movie of rows) {
      const card = make("a", "recent-movie-card"); card.href = "/movie-management.html";
      const poster = make("div", "recent-poster", "Bb."); const url = posterUrl(movie.posterUrl);
      if (url) { const image = make("img"); image.alt = `Poster ${movie.title}`; image.loading = "lazy"; image.referrerPolicy = "no-referrer"; image.addEventListener("error", () => { image.hidden = true; }); image.src = url; poster.append(image); }
      const status = Object.hasOwn(names, movie.status) ? movie.status : "ENDED";
      card.append(poster, make("span", `admin-status ${status}`, names[status]), make("h3", "", movie.title), make("p", "", `${movie.durationMinutes} phút · ${movie.ageRating}`)); $("recent-movies").append(card);
    }
  }
  async function load() {
    try {
      const user = await window.bbcinemaAdminReady;
      if (!user || user.role !== "ADMIN") return;
      const response = await fetch("/api/admin/overview", { credentials: "same-origin", headers: { Accept: "application/json" } });
      if (response.status === 401) { location.replace("/login"); return; }
      if (!response.ok) throw new Error("Chưa tải được tổng quan. Vui lòng tải lại trang để thử lại.");
      const data = await response.json();
      document.querySelectorAll("[data-count]").forEach((node) => { node.textContent = new Intl.NumberFormat("vi-VN").format(data.counts[node.dataset.count] ?? 0); });
      upcoming(data.upcomingShowtimes || []); movies(data.recentMovies || []);
      $("dashboard-updated").textContent = `Cập nhật ${dateTime(data.generatedAt)} · giờ Việt Nam`;
    } catch (error) {
      $("dashboard-message").hidden = false; $("dashboard-message").classList.add("error"); $("dashboard-message").textContent = error.message || "Không thể kết nối máy chủ.";
      upcoming([]); movies([]);
    }
  }
  void load();
})();
