"use strict";

(() => {
  const $ = (id) => document.getElementById(id);
  if (document.body.dataset.page !== "management") return;
  const state = { user: null, movies: [], cinemas: [], rooms: [], types: [], priceTypes: [], selected: null, page: 0,
    authEpoch: 0, listEpoch: 0, roomEpoch: 0, priceEpoch: 0, editEpoch: 0 };
  const names = { SCHEDULED: "Đã lên lịch", OPEN_FOR_SALE: "Mở bán", CANCELLED: "Đã hủy", COMPLETED: "Đã hoàn thành" };
  const money = new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" });
  const el = (tag, className, text) => { const node = document.createElement(tag); if (className) node.className = className; if (text !== undefined) node.textContent = text; return node; };

  function notify(text, error = false) { $("message").textContent = text; $("message").className = error ? "message error" : "message"; $("message").hidden = false; }
  async function api(path, method = "GET", body) {
    const options = { method, credentials: "same-origin", headers: { Accept: "application/json" } };
    if (method !== "GET") {
      const csrf = await api("/api/auth/csrf"); options.headers[csrf.headerName] = csrf.token;
      if (body !== undefined) { options.headers["Content-Type"] = "application/json"; options.body = JSON.stringify(body); }
    }
    const response = await fetch(path, options); let result = null;
    if (response.status !== 204) { try { result = await response.json(); } catch (_) { /* Keep the HTTP error for a non-JSON proxy response. */ } }
    if (!response.ok) { const error = new Error(result?.errors ? Object.values(result.errors).join("; ") : result?.message || `Yêu cầu thất bại (${response.status}).`); error.status = response.status; throw error; }
    return result;
  }
  async function run(action, button) {
    if (button) button.disabled = true;
    try { await action(); } catch (error) { if (error.status === 401 && state.user) setUser(null); notify(error.message || "Không thể kết nối máy chủ.", true); }
    finally { if (button?.isConnected) button.disabled = false; syncEditor(); }
  }
  function bind(id, action) { $(id).addEventListener("submit", (event) => { event.preventDefault(); const form = event.currentTarget; void run(() => action(form), event.submitter); }); }
  function options(select, values, empty, label) {
    select.replaceChildren(); const first = el("option", "", empty); first.value = ""; select.append(first);
    for (const item of values) { const option = el("option", "", label(item)); option.value = item.id; select.append(option); }
  }
  function dateTime(value) { const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}:\d{2})/.exec(value || ""); return match ? `${match[4]} · ${match[3]}/${match[2]}/${match[1]}` : value || "Đang cập nhật"; }
  function tag(status) { const key = Object.hasOwn(names, status) ? status : "SCHEDULED"; return el("span", `tag status-${key}`, names[key]); }
  function table(prices) {
    const node = el("table", "price-table"); const head = el("thead"), heading = el("tr");
    heading.append(el("th", "", "Loại ghế"), el("th", "", "Đơn vị"), el("th", "", "Giá cuối cùng")); head.append(heading); node.append(head);
    const body = el("tbody");
    for (const price of prices || []) { const row = el("tr"); row.append(el("td", "", price.seatTypeName), el("td", "", price.seatSpan === 2 ? "Cả cặp ghế đôi" : "Một ghế"), el("td", "", money.format(price.price))); body.append(row); }
    node.append(body); return node;
  }
  async function allMovies() {
    const result = []; let current = 0, pages = 1;
    while (current < pages) { const data = await api(`/api/admin/movies?size=100&page=${current}`); result.push(...data.items); pages = data.totalPages; current++; }
    return result;
  }
  async function loadCatalogs() {
    const epoch = state.authEpoch;
    const [movies, cinemas, types] = await Promise.all([allMovies(), api("/api/cinemas"), api("/api/seat-types")]);
    if (epoch !== state.authEpoch) return;
    state.movies = movies; state.cinemas = cinemas; state.types = types;
    options($("movie-filter"), movies, "Tất cả phim", (movie) => movie.title);
    options($("cinema-filter"), cinemas, "Tất cả rạp", (cinema) => cinema.cinemaName);
    options($("editor-movie"), movies.filter((m) => ["COMING_SOON", "NOW_SHOWING"].includes(m.status)), "Chọn phim", (movie) => `${movie.title} · ${movie.durationMinutes} phút`);
    options($("editor-cinema"), cinemas.filter((c) => c.status === "ACTIVE"), "Chọn rạp", (cinema) => cinema.cinemaName);
    await loadList(0);
  }
  function card(showtime) {
    const node = el("article", "card showtime-card"); node.append(tag(showtime.status), el("h3", "", showtime.movieTitle));
    node.append(el("p", "showtime-time", dateTime(showtime.startTime)), el("p", "", `Kết thúc ${dateTime(showtime.endTime)}`), el("p", "showtime-place", `${showtime.cinemaName} · ${showtime.auditoriumName}`), el("p", "", `${showtime.durationMinutes} phút · ${showtime.ageRating}`));
    const prices = showtime.prices || [];
    node.append(el("p", "", prices.length ? `Giá từ ${money.format(Math.min(...prices.map((p) => Number(p.price))))}` : "Chưa có giá ghế"));
    const edit = el("button", "secondary", "Chi tiết / quản lý"); edit.type = "button"; edit.addEventListener("click", () => void run(() => editShowtime(showtime.id), edit)); node.append(edit);
    return node;
  }
  async function loadList(number = 0) {
    const epoch = ++state.listEpoch;
    const query = new URLSearchParams({ page: String(number), size: "12" });
    for (const key of ["movieId", "cinemaId", "date", "status"]) { const value = $("filter-form").elements[key].value; if (value) query.set(key, value); }
    $("showtime-list").replaceChildren(); $("showtime-list").setAttribute("aria-busy", "true");
    $("list-empty").textContent = "Đang tải lịch chiếu…"; $("list-empty").hidden = false; $("previous-page").disabled = true; $("next-page").disabled = true;
    try {
      const result = await api(`/api/admin/showtimes?${query}`);
      if (epoch !== state.listEpoch) return;
      state.page = result.page; const fragment = document.createDocumentFragment();
      for (const showtime of result.items) fragment.append(card(showtime)); $("showtime-list").append(fragment);
      $("list-empty").hidden = result.items.length > 0; $("list-empty").textContent = "Chưa có suất chiếu phù hợp bộ lọc.";
      $("result-count").textContent = `${result.totalElements} suất chiếu`; $("page-label").textContent = `Trang ${result.totalPages ? result.page + 1 : 0} / ${result.totalPages}`;
      $("previous-page").disabled = result.page <= 0; $("next-page").disabled = result.page + 1 >= result.totalPages;
    } catch (error) { if (epoch !== state.listEpoch) return; $("list-empty").textContent = "Không tải được lịch. Bấm tìm/lọc lịch để thử lại."; throw error; }
    finally { if (epoch === state.listEpoch) $("showtime-list").setAttribute("aria-busy", "false"); }
  }
  function syncEditor() {
    const editable = !state.selected || state.selected.status === "SCHEDULED";
    $("save-showtime").disabled = !editable || state.user?.role !== "ADMIN";
    $("fill-prices").disabled = !editable || !state.priceTypes.length;
    $("save-status").disabled = !state.selected || ["CANCELLED", "COMPLETED"].includes(state.selected.status);
  }
  function updateEnd() {
    const movie = state.movies.find((m) => String(m.id) === $("editor-movie").value); const start = $("start-time").value;
    if (!movie || !start) { $("end-preview").textContent = "Chọn phim và giờ bắt đầu"; return; }
    const date = new Date(`${start.length === 16 ? start + ":00" : start}Z`);
    if (!Number.isFinite(date.getTime())) { $("end-preview").textContent = "Giờ không hợp lệ"; return; }
    date.setUTCMinutes(date.getUTCMinutes() + Number(movie.durationMinutes)); $("end-preview").textContent = `${dateTime(date.toISOString())} (${movie.durationMinutes} phút)`;
  }
  function renderPriceInputs(types, existing = []) {
    state.priceTypes = types; $("price-inputs").replaceChildren(); $("price-empty").hidden = types.length > 0;
    for (const type of types) {
      const label = el("label", "", type.typeName); const input = el("input"); input.type = "number"; input.name = `price_${type.id}`; input.dataset.seatTypeId = type.id; input.min = "0"; input.max = "9999999999.99"; input.step = "0.01"; input.required = true;
      const price = existing.find((item) => String(item.seatTypeId) === String(type.id)); input.value = price ? price.price : "";
      label.append(input, el("small", "", type.seatSpan === 2 ? "Giá cho cả cặp ghế đôi" : "Giá cho một ghế")); $("price-inputs").append(label);
    }
    syncEditor();
  }
  async function loadPrices(existing = []) {
    const epoch = ++state.priceEpoch, authEpoch = state.authEpoch, roomId = $("editor-room").value;
    renderPriceInputs([]); $("price-empty").textContent = roomId ? "Đang tải loại ghế của phòng…" : "Chọn phòng đã có sơ đồ ghế để thiết lập giá.";
    if (!roomId) return;
    const map = await api(`/api/auditoriums/${roomId}/seat-map`);
    if (epoch !== state.priceEpoch || authEpoch !== state.authEpoch) return;
    const ids = new Set(map.seats.filter((seat) => seat.status !== "AISLE").map((seat) => seat.seatTypeId));
    const types = state.types.filter((type) => ids.has(type.id)); renderPriceInputs(types, existing);
    $("price-empty").textContent = "Phòng chưa có ghế vật lý. Hãy tạo sơ đồ trong trang Rạp & phòng trước.";
  }
  async function loadRooms(roomId = "", existing = []) {
    const epoch = ++state.roomEpoch, authEpoch = state.authEpoch, cinemaId = $("editor-cinema").value; state.priceEpoch++; state.rooms = [];
    options($("editor-room"), [], cinemaId ? "Đang tải phòng…" : "Chọn rạp trước", (room) => room.name); $("editor-room").disabled = true; renderPriceInputs([]);
    if (!cinemaId) return;
    const rooms = await api(`/api/cinemas/${cinemaId}/auditoriums`);
    if (epoch !== state.roomEpoch || authEpoch !== state.authEpoch) return;
    state.rooms = rooms; options($("editor-room"), rooms.filter((room) => room.status === "ACTIVE"), "Chọn phòng", (room) => `${room.name} · ${room.totalRows} × ${room.totalCols}`); $("editor-room").disabled = false;
    if (roomId) { $("editor-room").value = roomId; await loadPrices(existing); }
  }
  function resetEditor() {
    state.editEpoch++; state.roomEpoch++; state.priceEpoch++; state.selected = null;
    $("showtime-form").reset(); options($("editor-room"), [], "Chọn rạp trước", (room) => room.name); $("editor-room").disabled = true;
    $("editor-title").textContent = "Thêm suất chiếu mới"; $("save-showtime").textContent = "Lưu suất chiếu mới"; $("status-panel").hidden = true;
    $("editor-card").classList.remove("editing"); $("editor-card").setAttribute("aria-busy", "false"); renderPriceInputs([]); updateEnd();
  }
  function renderStatus(showtime) {
    $("status-panel").hidden = false; $("status-title").textContent = `Suất #${showtime.id} · ${names[showtime.status] || showtime.status}`;
    $("status-summary").replaceChildren(el("p", "", `${showtime.movieTitle} · ${showtime.cinemaName} · ${showtime.auditoriumName} · ${dateTime(showtime.startTime)}`), table(showtime.prices));
    const choices = showtime.status === "SCHEDULED" ? ["OPEN_FOR_SALE", "CANCELLED", "COMPLETED"] : showtime.status === "OPEN_FOR_SALE" ? ["CANCELLED", "COMPLETED"] : [];
    $("next-status").replaceChildren(); for (const key of choices) { const option = el("option", "", names[key]); option.value = key; $("next-status").append(option); }
    $("status-form").hidden = !choices.length; syncEditor();
  }
  async function editShowtime(id) {
    const epoch = ++state.editEpoch, authEpoch = state.authEpoch; $("editor-card").setAttribute("aria-busy", "true");
    try {
      const showtime = await api(`/api/admin/showtimes/${id}`);
      if (epoch !== state.editEpoch || authEpoch !== state.authEpoch) return;
      state.selected = showtime; const form = $("showtime-form"); form.reset();
      $("editor-movie").value = showtime.movieId; $("editor-cinema").value = showtime.cinemaId; $("start-time").value = showtime.startTime.slice(0, 16); $("base-price").value = showtime.basePrice;
      $("editor-title").textContent = `Suất chiếu #${showtime.id}`; $("save-showtime").textContent = "Lưu thay đổi suất chiếu"; $("editor-card").classList.add("editing"); renderStatus(showtime); updateEnd();
      await loadRooms(showtime.auditoriumId, showtime.prices);
      if (epoch !== state.editEpoch || authEpoch !== state.authEpoch) return;
      $("editor-card").scrollIntoView({ behavior: "smooth", block: "start" }); notify(showtime.status === "SCHEDULED" ? "Đã nạp suất chiếu để chỉnh sửa hoặc mở bán." : "Suất này chỉ có thể cập nhật trạng thái theo quy trình.");
    } catch (error) { if (epoch === state.editEpoch && authEpoch === state.authEpoch) throw error; }
    finally { if (epoch === state.editEpoch) $("editor-card").setAttribute("aria-busy", "false"); }
  }
  function setUser(user) {
    if (!user && document.body.classList.contains("admin-ready")) { window.location.replace("/login"); return; }
    state.user = user; state.authEpoch++; state.listEpoch++; state.editEpoch++; state.roomEpoch++; state.priceEpoch++;
    $("login-panel").hidden = !!user; $("logout").hidden = !user; $("management-workspace").hidden = user?.role !== "ADMIN";
    $("account-label").textContent = user ? `${user.fullName} · ${user.role}` : "";
    if (!user || user.role !== "ADMIN") { state.selected = null; $("showtime-list").replaceChildren(); $("showtime-form").reset(); $("price-inputs").replaceChildren(); $("login-form").elements.password.value = ""; }
  }
  async function openManagement(user) { setUser(user); if (user.role !== "ADMIN") { notify("Tài khoản này không có quyền quản lý lịch chiếu.", true); return; } resetEditor(); await loadCatalogs(); }

  bind("filter-form", () => loadList(0));
  $("previous-page").addEventListener("click", () => void run(() => loadList(Math.max(0, state.page - 1)))); $("next-page").addEventListener("click", () => void run(() => loadList(state.page + 1)));
  bind("login-form", async (form) => { const data = Object.fromEntries(new FormData(form)); const user = await api("/api/auth/login", "POST", data); form.elements.password.value = ""; await openManagement(user); });
  $("logout").addEventListener("click", () => void run(async () => { await api("/api/auth/logout", "POST"); setUser(null); notify("Đã đăng xuất."); }, $("logout")));
  $("new-showtime").addEventListener("click", resetEditor); $("editor-movie").addEventListener("change", updateEnd); $("start-time").addEventListener("input", updateEnd);
  $("editor-cinema").addEventListener("change", () => void run(() => loadRooms())); $("editor-room").addEventListener("change", () => void run(() => loadPrices()));
  $("fill-prices").addEventListener("click", () => { const base = Number($("base-price").value); if (!$("base-price").reportValidity()) return; for (const type of state.priceTypes) $("showtime-form").elements[`price_${type.id}`].value = Math.round((base + Number(type.defaultSurcharge || 0)) * 100) / 100; });
  bind("showtime-form", async (form) => {
    if (!state.priceTypes.length) throw new Error("Chọn phòng đã có sơ đồ ghế trước khi lưu.");
    const data = Object.fromEntries(new FormData(form)); const prices = state.priceTypes.map((type) => ({ seatTypeId: type.id, price: Number(data[`price_${type.id}`]) }));
    const body = { movieId: Number(data.movieId), cinemaId: Number(data.cinemaId), auditoriumId: Number(data.auditoriumId), startTime: data.startTime.length === 16 ? `${data.startTime}:00` : data.startTime, basePrice: Number(data.basePrice), prices };
    const id = state.selected?.id, epoch = state.editEpoch, authEpoch = state.authEpoch;
    const saved = await api(`/api/admin/showtimes${id ? `/${id}` : ""}`, id ? "PUT" : "POST", body);
    if (authEpoch !== state.authEpoch) return;
    if (epoch === state.editEpoch) { state.selected = saved; $("editor-title").textContent = `Suất chiếu #${saved.id}`; $("save-showtime").textContent = "Lưu thay đổi suất chiếu"; $("editor-card").classList.add("editing"); renderStatus(saved); }
    await loadList(0); notify(id ? "Đã cập nhật suất chiếu." : "Đã tạo suất chiếu. Chọn Mở bán để khách xem được lịch và giá.");
  });
  bind("status-form", async (form) => {
    const id = state.selected?.id, epoch = state.editEpoch, authEpoch = state.authEpoch; if (!id) throw new Error("Chọn một suất chiếu trước.");
    const saved = await api(`/api/admin/showtimes/${id}/status`, "PATCH", { status: form.elements.status.value });
    if (authEpoch !== state.authEpoch) return;
    if (epoch === state.editEpoch) { state.selected = saved; renderStatus(saved); }
    await loadList(state.page); notify(`Đã chuyển suất #${id} sang ${names[saved.status] || saved.status}.`);
  });
  void run(async () => { const epoch = state.authEpoch; let user; try { user = await api("/api/users/me"); } catch (error) { if (epoch !== state.authEpoch) return; if (error.status === 401) { setUser(null); return; } throw error; } if (epoch === state.authEpoch) await openManagement(user); });
})();
