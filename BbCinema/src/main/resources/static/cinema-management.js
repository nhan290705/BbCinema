"use strict";

(() => {
  const $ = (id) => document.getElementById(id);
  const state = { user: null, provinces: [], cinemas: [], rooms: [], types: [], map: null, selectedSeat: null, epoch: 0 };
  const statusNames = { ACTIVE: "Hoạt động", MAINTENANCE: "Bảo trì", ARCHIVED: "Ngừng hoạt động", INACTIVE: "Ngừng hoạt động", AVAILABLE: "Hoạt động", BROKEN: "Hỏng", BLOCKED: "Khóa", AISLE: "Lối đi" };
  const money = new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" });
  const previousSelection = { province: "", cinema: "", room: "" };
  function rememberSelection() {
    previousSelection.province = $("province-select").value;
    previousSelection.cinema = $("cinema-select").value;
    previousSelection.room = $("auditorium-select").value;
  }
  function confirmRoomChange(select, previous) {
    if (window.BbcinemaSeatEditor && !window.BbcinemaSeatEditor.canLeave()) { select.value = previous; return false; }
    window.BbcinemaSeatEditor?.clear();
    return true;
  }

  function notify(message, kind = "success") {
    $("message").textContent = message;
    $("message").className = `message ${kind}`;
    $("message").hidden = false;
  }

  async function api(path, method = "GET", body) {
    const options = { method, credentials: "same-origin", headers: { Accept: "application/json" } };
    if (method !== "GET") {
      const csrf = await api("/api/auth/csrf");
      options.headers[csrf.headerName] = csrf.token;
      if (body !== undefined) {
        options.headers["Content-Type"] = "application/json";
        options.body = JSON.stringify(body);
      }
    }
    const response = await fetch(path, options);
    let result = null;
    if (response.status !== 204) {
      try { result = await response.json(); } catch (_) { /* Present a plain HTTP error if a proxy returns HTML. */ }
    }
    if (!response.ok) {
      const fields = result?.errors ? Object.values(result.errors).join("; ") : "";
      const error = new Error(fields || result?.message || `Yêu cầu thất bại (${response.status}).`);
      error.status = response.status;
      throw error;
    }
    return result;
  }

  async function run(action, button) {
    if (button) button.disabled = true;
    try { await action(); }
    catch (error) {
      if (error.status === 401 && state.user) setUser(null);
      notify(error.message || "Không thể kết nối máy chủ. Vui lòng thử lại.", "error");
    } finally {
      if (button?.isConnected) button.disabled = false;
      $("save-seat").disabled = !state.selectedSeat || state.user?.role !== "ADMIN";
      $("update-cinema").disabled = !$("cinema-select").value || state.user?.role !== "ADMIN";
    }
  }

  function bindForm(id, action) {
    $(id).addEventListener("submit", (event) => {
      event.preventDefault();
      const form = event.currentTarget;
      void run(() => action(Object.fromEntries(new FormData(form)), form), event.submitter);
    });
  }

  function setOptions(select, items, placeholder, text) {
    select.replaceChildren();
    const option = document.createElement("option"); option.value = ""; option.textContent = placeholder; select.append(option);
    for (const item of items) {
      const entry = document.createElement("option"); entry.value = item.id; entry.textContent = text(item); select.append(entry);
    }
  }

  function setUser(user) {
    if (!user && document.body.classList.contains("admin-ready")) { window.location.replace("/login"); return; }
    state.user = user;
    if (window.BbcinemaSeatEditor) {
      document.body.classList.toggle("seat-editor-active", user?.role === "ADMIN");
      $("seat-layout-editor").hidden = user?.role !== "ADMIN";
      if (!user || user.role !== "ADMIN") window.BbcinemaSeatEditor.clear();
    }
    state.epoch++;
    state.map = null;
    $("login-panel").hidden = !!user;
    $("workspace").hidden = !user;
    $("logout").hidden = !user;
    $("account-label").textContent = user ? `${user.fullName} · ${user.role}` : "Quản lý rạp và phòng chiếu";
    document.querySelectorAll(".admin-only").forEach((element) => { element.hidden = user?.role !== "ADMIN"; });
    if (!user) {
      state.cinemas = []; state.rooms = []; state.provinces = []; state.types = [];
      $("seat-grid").replaceChildren();
      $("login-form").elements.password.value = "";
    }
  }

  async function loadCatalogs() {
    if (window.BbcinemaSeatEditor?.isDirty()) {
      if (!window.BbcinemaSeatEditor.canLeave()) return;
      window.BbcinemaSeatEditor.clear();
    }
    const epoch = state.epoch;
    const [provinces, types] = await Promise.all([api("/api/provinces"), api("/api/seat-types")]);
    if (epoch !== state.epoch) return;
    state.provinces = provinces; state.types = types;
    renderProvinces();
    setOptions($("province-select"), provinces, "Tất cả tỉnh / thành", (p) => p.provinceName);
    setOptions($("cinema-province"), provinces, "Chọn tỉnh / thành", (p) => p.provinceName);
    renderTypes();
    window.BbcinemaSeatEditor?.refreshTypes(types);
    await loadCinemas();
  }

  function renderTypes() {
    const previous = $("default-seat-type").value;
    setOptions($("default-seat-type"), state.types, "Chọn loại ghế", (t) => `${t.typeName} · mã ${t.id}`);
    $("default-seat-type").value = state.types.some((t) => String(t.id) === previous) ? previous : String(state.types.find((t) => t.typeName === "STANDARD")?.id || "");
    $("seat-type-list").replaceChildren();
    for (const type of state.types) {
      const item = document.createElement("li");
      item.textContent = `Mã ${type.id}: ${type.typeName} · ${type.seatSpan} ô · phụ thu ${money.format(type.defaultSurcharge)}`;
      $("seat-type-list").append(item);
    }
  }

  function renderProvinces() {
    const catalog = $("province-catalog");
    if (!catalog) return;
    catalog.replaceChildren();
    if (!state.provinces.length) {
      const row = document.createElement("tr");
      const empty = document.createElement("td"); empty.colSpan = 3; empty.className = "admin-empty";
      empty.textContent = "Chưa có tỉnh / thành. Thêm danh mục ở biểu mẫu phía trên.";
      row.append(empty); catalog.append(row); return;
    }
    for (const province of state.provinces) {
      const row = document.createElement("tr");
      for (const value of [province.id, province.code || "—", province.provinceName]) {
        const cell = document.createElement("td"); cell.textContent = value; row.append(cell);
      }
      catalog.append(row);
    }
  }

  function resetMap(message = "Chọn rạp và phòng để xem sơ đồ.") {
    state.map = null; state.selectedSeat = null;
    $("seat-grid").replaceChildren();
    $("map-empty").hidden = false; $("map-empty").textContent = message;
    $("map-title").textContent = "Chọn một phòng chiếu";
    $("map-subtitle").textContent = "Các ghế được hiển thị theo đúng hàng và cột trong phòng.";
    $("room-status").hidden = true; $("save-seat").disabled = true;
    $("seat-details").textContent = "Chọn một ghế để xem thông tin.";
  }

  async function loadCinemas(selectId = "") {
    const epoch = ++state.epoch;
    resetMap();
    state.cinemas = []; state.rooms = [];
    setOptions($("cinema-select"), [], "Đang tải rạp…", (c) => c.cinemaName);
    setOptions($("auditorium-select"), [], "Chọn phòng chiếu", (a) => a.name);
    $("auditorium-select").disabled = true; $("update-cinema").disabled = true;
    const province = $("province-select").value;
    const cinemas = await api(`/api/cinemas${province ? `?provinceId=${encodeURIComponent(province)}` : ""}`);
    if (epoch !== state.epoch) return;
    state.cinemas = cinemas;
    setOptions($("cinema-select"), cinemas, cinemas.length ? "Chọn rạp chiếu" : "Chưa có rạp trong danh mục", (c) => c.cinemaName);
    rememberSelection();
    if (selectId && cinemas.some((c) => String(c.id) === String(selectId))) {
      $("cinema-select").value = selectId;
      await loadRooms();
    }
  }

  async function loadRooms(selectId = "") {
    const epoch = ++state.epoch;
    resetMap(); state.rooms = [];
    const cinemaId = $("cinema-select").value;
    setOptions($("auditorium-select"), [], cinemaId ? "Đang tải phòng…" : "Chọn phòng chiếu", (a) => a.name);
    $("auditorium-select").disabled = true;
    $("update-cinema").disabled = !cinemaId;
    const cinema = state.cinemas.find((c) => String(c.id) === cinemaId);
    if (cinema) {
      const form = $("cinema-form");
      for (const key of ["provinceId", "cinemaName", "address", "hotline", "latitude", "longitude", "status"]) form.elements[key].value = cinema[key] ?? "";
    }
    if (!cinemaId) return;
    const rooms = await api(`/api/cinemas/${cinemaId}/auditoriums`);
    if (epoch !== state.epoch) return;
    state.rooms = rooms;
    setOptions($("auditorium-select"), rooms, rooms.length ? "Chọn phòng chiếu" : "Rạp chưa có phòng chiếu", (a) => `${a.name} · ${a.totalRows} × ${a.totalCols}`);
    $("auditorium-select").disabled = !rooms.length;
    rememberSelection();
    if (selectId && rooms.some((a) => String(a.id) === String(selectId))) {
      $("auditorium-select").value = selectId;
      await loadMap();
    }
  }

  async function loadMap() {
    if (window.BbcinemaSeatEditor?.isDirty() && !window.BbcinemaSeatEditor.canLeave()) return;
    const epoch = ++state.epoch;
    const roomId = $("auditorium-select").value;
    resetMap(roomId ? "Đang tải sơ đồ ghế…" : "Chọn phòng để xem sơ đồ.");
    rememberSelection();
    if (!roomId) { window.BbcinemaSeatEditor?.clear(); return; }
    const map = await api(`/api/auditoriums/${roomId}/seat-map`);
    if (epoch !== state.epoch) return;
    state.map = map; renderMap();
    if (state.user?.role === "ADMIN") {
      await window.BbcinemaSeatEditor?.load(roomId, { user: state.user, types: state.types, bridge: {
        refreshMap: async (saved) => {
          const room = state.rooms.find((entry) => entry.id === saved.auditoriumId);
          if (room) { room.totalRows = saved.totalRows; room.totalCols = saved.totalCols; }
          state.map = await api(`/api/auditoriums/${saved.auditoriumId}/seat-map`); renderMap();
        },
        selectRoom: async (id) => { await loadRooms(id); },
        refreshTypes: async () => { state.types = await api("/api/seat-types"); renderTypes(); window.BbcinemaSeatEditor?.refreshTypes(state.types); }
      } });
    }
  }

  function rowLabel(index) {
    let result = "";
    for (let number = index + 1; number > 0; number = Math.floor((number - 1) / 26)) result = String.fromCharCode(65 + (number - 1) % 26) + result;
    return result;
  }

  function renderMap() {
    const map = state.map;
    $("map-title").textContent = `${map.cinemaName} · ${map.auditoriumName}`;
    $("map-subtitle").textContent = `${map.totalRows} hàng × ${map.totalCols} cột · ${map.seats.length} vị trí được cấu hình`;
    $("room-status").textContent = statusNames[map.status] || map.status; $("room-status").hidden = false;
    $("room-status-form").elements.status.value = map.status;
    $("map-empty").hidden = !!map.seats.length;
    $("map-empty").textContent = "Phòng chưa có sơ đồ ghế. Chọn mục Sơ đồ ghế trong menu bên trái để tạo.";
    const grid = $("seat-grid"); grid.replaceChildren();
    if (!map.seats.length) return;
    const settings = { seatWidth: 38, seatHeight: 36, columnGap: 7, rowGap: 7, screenPosition: "TOP", ...map.settings };
    grid.style.gridTemplateColumns = `24px repeat(${map.totalCols}, ${settings.seatWidth}px)`;
    grid.style.gridTemplateRows = `20px repeat(${map.totalRows}, ${settings.seatHeight}px)`;
    grid.style.columnGap = `${settings.columnGap}px`; grid.style.rowGap = `${settings.rowGap}px`;
    const screen = document.querySelector("[data-legacy-seat-view] .screen"), scroll = document.querySelector("[data-legacy-seat-view] .map-scroll");
    if (screen && scroll) { if (settings.screenPosition === "BOTTOM") scroll.after(screen); else scroll.before(screen); }
    const fragment = document.createDocumentFragment();
    const addLabel = (text, x, y) => {
      const label = document.createElement("span"); label.className = "grid-label"; label.textContent = text;
      label.style.gridColumn = x; label.style.gridRow = y; fragment.append(label);
    };
    for (let x = 0; x < map.totalCols; x++) addLabel(x + 1, x + 2, 1);
    for (let y = 0; y < map.totalRows; y++) addLabel(map.seats.find((seat) => seat.gridY === y)?.rowLabel || rowLabel(y), 1, y + 2);
    for (const seat of map.seats) {
      const button = document.createElement("button"); button.type = "button";
      const typeClass = seat.seatSpan === 2 ? "couple" : seat.seatTypeName === "VIP" ? "vip" : "standard";
      const statusClass = { BROKEN: "broken", BLOCKED: "blocked", AISLE: "aisle" }[seat.status] || "";
      button.className = `seat ${typeClass} ${statusClass}`;
      button.style.gridColumn = `${seat.gridX + 2} / span ${seat.widthUnits || seat.seatSpan}`; button.style.gridRow = seat.gridY + 2;
      button.style.height = `${settings.seatHeight}px`; button.style.minHeight = "0";
      button.textContent = seat.status === "AISLE" ? "·" : `${seat.rowLabel}${seat.colNumber}`;
      button.title = `${seat.rowLabel}${seat.colNumber} · ${seat.seatTypeName} · ${statusNames[seat.status] || seat.status}`;
      button.setAttribute("aria-label", button.title); button.setAttribute("aria-pressed", "false");
      button.addEventListener("click", () => selectSeat(seat, button));
      fragment.append(button);
    }
    grid.append(fragment);
  }

  function selectSeat(seat, button) {
    state.selectedSeat = seat;
    $("seat-grid").querySelectorAll(".seat").forEach((item) => { item.classList.remove("selected"); item.setAttribute("aria-pressed", "false"); });
    button.classList.add("selected"); button.setAttribute("aria-pressed", "true");
    $("seat-details").textContent = `Ghế ${seat.rowLabel}${seat.colNumber} · ${seat.seatTypeName} · ${seat.seatSpan} ô · ${statusNames[seat.status] || seat.status} · Phụ thu ${money.format(seat.defaultSurcharge)}`;
    $("seat-status-form").elements.status.value = seat.status;
    $("save-seat").disabled = state.user?.role !== "ADMIN";
  }

  function requireRoom() { const id = $("auditorium-select").value; if (!id) throw new Error("Vui lòng chọn phòng chiếu ở phía trên."); return id; }
  function requireCinema() { const id = $("cinema-select").value; if (!id) throw new Error("Vui lòng chọn rạp chiếu ở phía trên."); return id; }

  function cinemaBody(data) {
    return { ...data, provinceId: Number(data.provinceId), hotline: data.hotline || null,
      latitude: data.latitude === "" ? null : Number(data.latitude), longitude: data.longitude === "" ? null : Number(data.longitude) };
  }

  bindForm("login-form", async (data, form) => {
    const user = await api("/api/auth/login", "POST", data); form.elements.password.value = "";
    setUser(user); await loadCatalogs(); notify("Đăng nhập thành công. Chọn rạp và phòng để xem sơ đồ.");
  });
  $("logout").addEventListener("click", () => void run(async () => { await api("/api/auth/logout", "POST"); setUser(null); notify("Đã đăng xuất."); }, $("logout")));
  $("province-select").addEventListener("change", () => { if (confirmRoomChange($("province-select"), previousSelection.province)) void run(() => loadCinemas()); });
  $("cinema-select").addEventListener("change", () => { if (confirmRoomChange($("cinema-select"), previousSelection.cinema)) void run(() => loadRooms()); });
  $("auditorium-select").addEventListener("change", () => { if (confirmRoomChange($("auditorium-select"), previousSelection.room)) void run(() => loadMap()); });

  bindForm("province-form", async (data, form) => {
    await api("/api/provinces", "POST", data); form.reset(); await loadCatalogs(); notify("Đã thêm tỉnh / thành.");
  });
  bindForm("cinema-form", async (data) => {
    const cinema = await api("/api/cinemas", "POST", cinemaBody(data));
    $("province-select").value = ""; await loadCinemas(cinema.id); notify("Đã thêm rạp. Có thể tạo phòng cho rạp này.");
  });
  $("update-cinema").addEventListener("click", () => void run(async () => {
    const form = $("cinema-form"); if (!form.reportValidity()) return;
    const id = requireCinema(); await api(`/api/cinemas/${id}`, "PUT", cinemaBody(Object.fromEntries(new FormData(form))));
    $("province-select").value = ""; await loadCinemas(id); notify("Đã cập nhật rạp.");
  }, $("update-cinema")));
  bindForm("auditorium-form", async (data, form) => {
    const room = await api(`/api/cinemas/${requireCinema()}/auditoriums`, "POST", { ...data, totalRows: Number(data.totalRows), totalCols: Number(data.totalCols) });
    form.reset(); await loadRooms(room.id); notify("Đã thêm phòng. Chọn mục Sơ đồ ghế trong menu bên trái để tạo ghế.");
  });
  bindForm("seat-type-form", async (data, form) => {
    await api("/api/seat-types", "POST", { ...data, seatSpan: Number(data.seatSpan), defaultSurcharge: Number(data.defaultSurcharge) });
    form.reset(); state.types = await api("/api/seat-types"); renderTypes(); notify("Đã thêm loại ghế.");
    window.BbcinemaSeatEditor?.refreshTypes(state.types);
  });
  bindForm("generate-form", async (data) => {
    const roomId = requireRoom(); const rowTypes = {};
    if (data.rowTypes.trim()) {
      for (const token of data.rowTypes.split(",")) {
        const pair = token.trim().toUpperCase().match(/^([A-Z]{1,5})\s*:\s*([1-9][0-9]*)$/);
        if (!pair) throw new Error("Loại ghế theo hàng phải có dạng C:2,D:2,H:3.");
        if (Object.hasOwn(rowTypes, pair[1])) throw new Error(`Hàng ${pair[1]} được khai báo nhiều lần.`);
        rowTypes[pair[1]] = Number(pair[2]);
      }
    }
    await api(`/api/auditoriums/${roomId}/seats/generate`, "POST", { defaultSeatTypeId: Number(data.defaultSeatTypeId), rowTypes });
    if ($("auditorium-select").value === roomId) await loadMap(); notify("Đã tạo sơ đồ ghế.");
  });
  bindForm("room-status-form", async (data) => {
    const roomId = requireRoom(); await api(`/api/auditoriums/${roomId}/status`, "PATCH", data);
    if ($("auditorium-select").value === roomId) await loadMap(); notify("Đã cập nhật trạng thái phòng.");
  });
  bindForm("seat-status-form", async (data) => {
    const roomId = requireRoom(); const seat = state.selectedSeat;
    if (!seat) throw new Error("Vui lòng chọn ghế trên sơ đồ.");
    await api(`/api/auditoriums/${roomId}/seats/${seat.id}/status`, "PATCH", data);
    if ($("auditorium-select").value === roomId) await loadMap(); notify(`Đã cập nhật ghế ${seat.rowLabel}${seat.colNumber}.`);
  });

  void run(async () => {
    const epoch = state.epoch;
    try {
      const user = await api("/api/users/me");
      if (epoch !== state.epoch) return;
      setUser(user);
    }
    catch (error) {
      if (epoch !== state.epoch) return;
      if (error.status === 401) { setUser(null); return; }
      throw error;
    }
    await loadCatalogs();
  });
})();
