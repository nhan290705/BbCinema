"use strict";

((global) => {
  const copy = (value) => JSON.parse(JSON.stringify(value));
  const cellKey = (x, y) => `${x},${y}`;
  const rowLabel = (index) => { let text = ""; for (let n = index + 1; n > 0; n = Math.floor((n - 1) / 26)) text = String.fromCharCode(65 + (n - 1) % 26) + text; return text; };
  const width = (seat) => Number(seat.widthUnits || seat.seatSpan || 1);
  const settingsDefault = { seatWidth: 38, seatHeight: 36, columnGap: 7, rowGap: 7, screenPosition: "TOP" };
  function occupancy(layout) {
    const result = new Map();
    for (const seat of layout.seats) for (let x = seat.gridX; x < seat.gridX + width(seat); x++) result.set(cellKey(x, seat.gridY), seat);
    return result;
  }
  function selectedSeats(layout, selected) { return layout.seats.filter((seat) => Array.from({ length: width(seat) }, (_, offset) => cellKey(seat.gridX + offset, seat.gridY)).some((key) => selected.has(key))); }
  function expandSelection(layout, selected) { const result = new Set(selected); selectedSeats(layout, selected).forEach((seat) => { for (let x = seat.gridX; x < seat.gridX + width(seat); x++) result.add(cellKey(x, seat.gridY)); }); return result; }
  function labelForRow(layout, y, reservedLabels = []) {
    const current = layout.seats.find((seat) => seat.gridY === y);
    if (current) return current.rowLabel;
    const used = new Set([...layout.seats.filter((seat) => seat.gridY !== y).map((seat) => seat.rowLabel), ...reservedLabels]);
    let label = rowLabel(y), next = 0;
    while (used.has(label)) label = rowLabel(next++);
    return label;
  }
  function toggleSelection(layout, selected, key) {
    const result = new Set(selected), seat = occupancy(layout).get(key);
    const footprint = seat ? Array.from({ length: width(seat) }, (_, offset) => cellKey(seat.gridX + offset, seat.gridY)) : [key];
    const removing = footprint.every((cell) => result.has(cell));
    footprint.forEach((cell) => { if (removing) result.delete(cell); else result.add(cell); });
    return expandSelection(layout, result);
  }
  function validate(layout) {
    if (!Number.isInteger(layout.totalRows) || !Number.isInteger(layout.totalCols) || layout.totalRows < 1 || layout.totalRows > 100 || layout.totalCols < 1 || layout.totalCols > 100 || layout.totalRows * layout.totalCols > 5000) throw new Error("Phòng cần 1–100 hàng/cột và tối đa 5.000 ô.");
    const occupied = new Set(), names = new Set(), rowNames = new Map(), rowIndexes = new Map();
    for (const seat of layout.seats) {
      if (!Number.isInteger(seat.gridX) || !Number.isInteger(seat.gridY) || seat.gridX < 0 || seat.gridY < 0 || seat.gridY >= layout.totalRows || seat.gridX + width(seat) > layout.totalCols) throw new Error("Ghế nằm ngoài sơ đồ. Hãy mở rộng phòng trước.");
      if (!Number.isInteger(width(seat)) || width(seat) < Number(seat.seatSpan || 1) || width(seat) > 4) throw new Error("Ghế rộng 1–4 ô; ghế đôi cần ít nhất 2 ô.");
      if (!/^[A-Z0-9]{1,5}$/.test(seat.rowLabel) || !Number.isInteger(seat.colNumber) || seat.colNumber < 1 || seat.colNumber > 999) throw new Error("Nhãn hàng gồm tối đa 5 chữ/số; số ghế từ 1–999.");
      if (rowNames.has(seat.gridY) && rowNames.get(seat.gridY) !== seat.rowLabel) throw new Error("Các ghế cùng hàng cần cùng một nhãn hàng.");
      if (rowIndexes.has(seat.rowLabel) && rowIndexes.get(seat.rowLabel) !== seat.gridY) throw new Error("Nhãn hàng không được trùng ở hai vị trí khác nhau.");
      rowNames.set(seat.gridY, seat.rowLabel); rowIndexes.set(seat.rowLabel, seat.gridY);
      const name = `${seat.rowLabel}:${seat.colNumber}`;
      if (names.has(name)) throw new Error(`Trùng số ghế ${seat.rowLabel}${seat.colNumber}. Hãy đánh số lại hàng.`);
      names.add(name);
      for (let x = seat.gridX; x < seat.gridX + width(seat); x++) { const key = cellKey(x, seat.gridY); if (occupied.has(key)) throw new Error("Các ghế đang chồng nhau. Chọn thêm vùng liền kề hoặc giảm chiều rộng."); occupied.add(key); }
    }
    return layout;
  }
  function renumber(layout, rows, start = 1) {
    for (const y of rows) layout.seats.filter((seat) => seat.gridY === y).sort((a, b) => a.gridX - b.gridX).forEach((seat, index) => { seat.colNumber = start + index; });
    return validate(layout);
  }
  function paint(layout, selected, type, requestedWidth) {
    if (!selected.size) throw new Error("Chọn ghế, hàng, cột hoặc vùng trống trước.");
    if (!type) throw new Error("Chọn một loại ghế trước.");
    const result = copy(layout), chosen = expandSelection(layout, selected), old = occupancy(result);
    const originalLabels = new Map(layout.seats.map((seat) => [seat.gridY, seat.rowLabel]));
    const reservedLabels = new Set(originalLabels.values());
    const size = Math.max(Number(type.seatSpan), Number(requestedWidth));
    if (!Number.isInteger(size) || size > 4) throw new Error("Chiều rộng không hợp lệ.");
    const owners = new Set(selectedSeats(result, chosen));
    result.seats = result.seats.filter((seat) => !owners.has(seat));
    const rows = new Set();
    for (let y = 0; y < result.totalRows; y++) {
      const columns = [];
      for (let x = 0; x < result.totalCols; x++) if (chosen.has(cellKey(x, y))) columns.push(x);
      const label = originalLabels.get(y) || labelForRow(result, y, reservedLabels);
      if (columns.length) reservedLabels.add(label);
      for (let index = 0; index < columns.length;) {
        let end = index + 1;
        while (end < columns.length && columns[end] === columns[end - 1] + 1) end++;
        if ((end - index) % size !== 0) throw new Error(`Vùng chọn ở hàng ${labelForRow(layout, y)} cần chia hết cho ${size} ô. Chọn thêm ô trống hoặc dùng ghế hẹp hơn.`);
        for (let i = index; i < end; i += size) {
          const x = columns[i], previous = old.get(cellKey(x, y));
          result.seats.push({ id: previous?.gridX === x ? previous.id : null, rowLabel: label, colNumber: x + 1,
            gridX: x, gridY: y, seatTypeId: Number(type.id), seatTypeName: type.typeName, seatSpan: Number(type.seatSpan),
            widthUnits: size, defaultSurcharge: type.defaultSurcharge, status: "AVAILABLE" });
          rows.add(y);
        }
        index = end;
      }
    }
    return renumber(result, rows);
  }
  function resizeSeats(layout, selected, requestedWidth) {
    const result = copy(layout), chosen = selectedSeats(result, selected), chosenSet = new Set(chosen), retained = result.seats.filter((seat) => !chosenSet.has(seat));
    if (!chosen.length) throw new Error("Chọn ghế cần giãn chiều rộng.");
    for (const seat of chosen.sort((a, b) => a.gridY - b.gridY || a.gridX - b.gridX)) {
      const covering = retained.find((other) => other.gridY === seat.gridY && other.gridX <= seat.gridX && other.gridX + width(other) > seat.gridX);
      if (covering) {
        if (chosenSet.has(covering) && covering.seatTypeId === seat.seatTypeId) continue;
        throw new Error("Vùng rộng mới chạm ghế khác. Chọn thêm các ghế cùng loại để gộp hoặc chừa ô trống.");
      }
      seat.widthUnits = Math.max(Number(requestedWidth), Number(seat.seatSpan)); retained.push(seat);
    }
    result.seats = retained;
    return renumber(result, new Set(chosen.map((seat) => seat.gridY)));
  }
  function setStatus(layout, selected, status) {
    const result = copy(layout), chosen = selectedSeats(result, selected);
    if (!chosen.length) throw new Error("Chọn ít nhất một ghế đã có trong sơ đồ.");
    chosen.forEach((seat) => { seat.status = status; }); return result;
  }
  function remove(layout, selected) { const result = copy(layout), owners = new Set(selectedSeats(result, selected)); result.seats = result.seats.filter((seat) => !owners.has(seat)); return result; }
  function preset(layout, types, kind) {
    const result = copy(layout); result.seats = [];
    const standard = types.find((type) => type.typeName === "STANDARD") || types.find((type) => type.seatSpan === 1) || types[0];
    if (!standard) throw new Error("Hãy tạo ít nhất một loại ghế trước.");
    const vip = types.find((type) => type.typeName === "VIP") || standard;
    const couple = types.find((type) => type.seatSpan === 2);
    const cols = result.totalCols, middle = Math.floor(cols / 2), aisle = kind === "compact" || cols < 8 ? [] : cols >= 14 ? [middle - 1, middle] : [middle];
    for (let y = 0; y < result.totalRows; y++) {
      const pairRow = couple && kind !== "premium" && y >= result.totalRows - (kind === "couple" ? 2 : 1);
      const type = pairRow ? couple : kind === "premium" || y >= Math.floor(result.totalRows * .5) ? vip : standard;
      const size = pairRow || kind === "premium" ? 2 : Number(type.seatSpan);
      let number = 1;
      for (let x = 0; x < cols;) {
        if (aisle.includes(x) || aisle.some((col) => col >= x && col < x + size) || x + size > cols) { x++; continue; }
        result.seats.push({ id: null, rowLabel: rowLabel(y), colNumber: number++, gridX: x, gridY: y, seatTypeId: type.id,
          seatTypeName: type.typeName, seatSpan: type.seatSpan, widthUnits: Math.max(size, type.seatSpan), status: "AVAILABLE", defaultSurcharge: type.defaultSurcharge });
        x += Math.max(size, type.seatSpan);
      }
    }
    return validate(result);
  }
  function payload(layout) { return { revision: layout.revision, totalRows: layout.totalRows, totalCols: layout.totalCols, settings: copy(layout.settings),
    seats: layout.seats.map(({ id, rowLabel, colNumber, gridX, gridY, seatTypeId, widthUnits, seatSpan, status }) => ({ id: id || null, rowLabel, colNumber, gridX, gridY, seatTypeId, widthUnits: widthUnits || seatSpan || 1, status })) }; }
  global.BbcinemaSeatLayoutModel = { copy, cellKey, rowLabel, width, occupancy, selectedSeats, expandSelection, toggleSelection, validate, paint, resizeSeats, setStatus, remove, renumber, preset, payload, labelForRow };
  if (typeof document === "undefined") return;
  const host = document.getElementById("seat-layout-editor");
  if (!host) return;
  const $ = (id) => document.getElementById(`sle-${id}`);
  const state = { layout: null, baseline: "", selected: new Set(), anchor: null, undo: [], redo: [], types: [], epoch: 0, busy: false, preview: false, zoom: 100, drag: null, bridge: null, requestedRoomId: null };
  host.innerHTML = `
    <div class="sle-empty" id="sle-empty">Chọn rạp và phòng ở phía trên để thiết kế sơ đồ ghế.</div>
    <button type="button" class="secondary" id="sle-load-retry" data-sle-action="retry-load" hidden>Thử tải lại sơ đồ</button>
    <div id="sle-workspace" hidden>
      <header class="sle-heading"><div><p>SEAT STUDIO</p><h3 id="sle-title">Thiết kế phòng chiếu</h3><span id="sle-summary"></span></div><div class="sle-main-actions"><span id="sle-dirty" hidden>Chưa lưu</span><button type="button" data-sle-action="reload" class="secondary">Tải lại</button><button type="button" data-sle-action="save" id="sle-save">Lưu sơ đồ</button></div></header>
      <p id="sle-message" class="sle-message" role="status" hidden></p>
      <div id="sle-lock" class="sle-lock" hidden></div>
      <div class="sle-commandbar"><div><button type="button" data-sle-action="undo" id="sle-undo" class="secondary" title="Ctrl+Z">↶ Hoàn tác</button><button type="button" data-sle-action="redo" id="sle-redo" class="secondary" title="Ctrl+Y">↷ Làm lại</button></div><div><button type="button" data-sle-action="select-all" class="secondary">Chọn tất cả</button><button type="button" data-sle-action="clear-selection" class="secondary">Bỏ chọn</button><button type="button" data-sle-action="preview" id="sle-preview" class="secondary" aria-pressed="false">Xem trước</button><label>Thu phóng <select id="sle-zoom"><option value="70">70%</option><option value="85">85%</option><option value="100" selected>100%</option><option value="120">120%</option><option value="150">150%</option></select></label></div></div>
      <div class="sle-layout"><aside class="sle-inspector">
        <section><h4>Vùng đang chọn <span id="sle-selection-count">0 ô</span></h4><p class="sle-hint">Bấm ghế hoặc ô trống. Kéo để chọn vùng; Shift+bấm để chọn hình chữ nhật; Ctrl+bấm để thêm/bớt. Bấm nhãn hàng/cột để chọn trọn hàng/cột.</p>
          <label>Loại ghế<select id="sle-type"></select></label><label>Chiều rộng chiếm chỗ<select id="sle-width"><option value="1">1 ô — ghế đơn</option><option value="2">2 ô — rộng / ghế đôi</option><option value="3">3 ô — ghế rộng</option><option value="4">4 ô — ghế rất rộng</option></select></label>
          <div class="sle-two"><button type="button" data-sle-action="paint" data-sle-structure>Đặt / đổi loại</button><button type="button" data-sle-action="width" data-sle-structure class="secondary">Áp dụng độ rộng</button></div><p class="sle-hint">Ghế đôi tối thiểu 2 ô. Đổi loại/gộp vùng chọn sẽ đánh số lại các hàng liên quan; chỉ ghế trong vùng chọn được thay thế. Tăng độ rộng có thể gộp các ghế cùng loại đã chọn; dùng Hoàn tác để khôi phục.</p>
          <label>Tình trạng<select id="sle-status"><option value="AVAILABLE">Hoạt động</option><option value="BROKEN">Hỏng</option><option value="BLOCKED">Khóa</option><option value="AISLE">Lối đi</option></select></label><button type="button" data-sle-action="status" class="secondary">Áp dụng trạng thái</button>
          <div class="sle-two"><button type="button" data-sle-action="aisle" data-sle-structure class="secondary">Tạo lối đi</button><button type="button" data-sle-action="remove" data-sle-structure class="secondary sle-danger">Xóa ghế / chừa trống</button></div>
        </section>
        <section><h4>Nhãn hàng & số ghế</h4><label>Nhãn mới cho một hàng<input id="sle-row-label" maxlength="5" placeholder="Ví dụ: A, VIP1"></label><button type="button" data-sle-action="label" data-sle-structure class="secondary">Đổi nhãn hàng đã chọn</button><label>Số bắt đầu<input id="sle-number-start" type="number" min="1" max="999" value="1"></label><button type="button" data-sle-action="renumber" data-sle-structure class="secondary">Đánh số các hàng đã chọn</button></section>
        <details><summary>Kích thước & mẫu bố trí</summary><div class="sle-two"><label>Số hàng<input id="sle-rows" type="number" min="1" max="100"></label><label>Số cột<input id="sle-cols" type="number" min="1" max="100"></label></div><button type="button" data-sle-action="resize" data-sle-structure class="secondary">Đổi kích thước phòng</button><label>Mẫu bố trí<select id="sle-preset"><option value="standard">Tiêu chuẩn · lối đi giữa, VIP và đôi cuối phòng</option><option value="premium">Phòng rộng · ghế VIP 2 ô</option><option value="couple">Gia đình · 2 hàng ghế đôi cuối</option><option value="compact">Phòng nhỏ · bố trí liền hàng</option></select></label><button type="button" data-sle-action="preset" data-sle-structure class="secondary">Áp dụng mẫu cho cả phòng</button><button type="button" data-sle-action="empty" data-sle-structure class="secondary sle-danger">Làm trống toàn bộ sơ đồ</button></details>
        <details><summary>Thêm loại ghế riêng</summary><label>Tên loại<input id="sle-new-type" maxlength="50" placeholder="RECLINER, PREMIUM, WHEELCHAIR…"></label><div class="sle-two"><label>Số chỗ / đơn vị<select id="sle-new-span"><option value="1">1 — ghế đơn</option><option value="2">2 — ghế đôi</option></select></label><label>Phụ thu mặc định<input id="sle-new-surcharge" type="number" min="0" max="9999999999.99" step="0.01" value="0"></label></div><button type="button" data-sle-action="new-type" class="secondary">Tạo loại ghế</button></details>
        <details><summary>Nhân bản sang phòng mới</summary><p class="sle-hint">Bản sao dùng sơ đồ đã lưu, cùng rạp, có ID ghế mới và trạng thái hoạt động. Có thể thiết kế bản sao ngay cả khi phòng gốc đang có suất chiếu.</p><label>Tên phòng mới<input id="sle-copy-name" maxlength="100"></label><button type="button" data-sle-action="copy" id="sle-copy" class="secondary">Tạo phòng bản sao</button></details>
      </aside><div class="sle-stage-column">
        <div class="sle-display-settings"><label>Rộng ô ghế <input type="range" min="24" max="80" id="sle-seatWidth" data-sle-setting="seatWidth"><output id="sle-seatWidth-value"></output></label><label>Cao ghế <input type="range" min="24" max="64" id="sle-seatHeight" data-sle-setting="seatHeight"><output id="sle-seatHeight-value"></output></label><label>Cách cột <input type="range" min="0" max="40" id="sle-columnGap" data-sle-setting="columnGap"><output id="sle-columnGap-value"></output></label><label>Cách hàng <input type="range" min="0" max="60" id="sle-rowGap" data-sle-setting="rowGap"><output id="sle-rowGap-value"></output></label><label>Màn hình<select id="sle-screen"><option value="TOP">Phía trên</option><option value="BOTTOM">Phía dưới</option></select></label></div>
        <div class="sle-viewport" id="sle-viewport"><div class="sle-stage" id="sle-stage"><div id="sle-screen-top" class="sle-screen">MÀN HÌNH</div><div id="sle-grid" class="sle-grid" role="group" aria-label="Trình thiết kế sơ đồ ghế"></div><div id="sle-screen-bottom" class="sle-screen sle-screen-bottom" hidden>MÀN HÌNH</div></div></div>
        <div class="sle-legend" id="sle-legend"></div><p class="sle-footnote">Màu ghế phân biệt loại; gạch chéo là ghế hỏng, biểu tượng khóa là ghế khóa. Các ô trống được giữ đúng vị trí, không dồn ghế.</p>
      </div></div>
    </div>`;

  function message(text, error = false) { $("message").textContent = text; $("message").className = `sle-message${error ? " error" : ""}`; $("message").hidden = false; }
  const signature = (layout) => JSON.stringify(payload(layout));
  const dirty = () => !!state.layout && signature(state.layout) !== state.baseline;
  function canLeave() { if (!dirty()) return true; return global.confirm("Sơ đồ có thay đổi chưa lưu. Bạn muốn bỏ các thay đổi và chuyển phòng?"); }
  function selectedRows() { return new Set([...state.selected].map((key) => Number(key.split(",")[1]))); }
  function typeColor(typeId) { const colors = ["#0b9991", "#bb8b32", "#9867ac", "#638a48", "#b36757", "#4c7da5", "#9b7b50"]; const index = state.types.findIndex((type) => type.id === Number(typeId)); return colors[Math.max(index, 0) % colors.length]; }
  async function api(path, method = "GET", body) {
    const options = { method, credentials: "same-origin", headers: { Accept: "application/json" } };
    if (method !== "GET") { const csrf = await api("/api/auth/csrf"); options.headers[csrf.headerName] = csrf.token; options.headers["Content-Type"] = "application/json"; options.body = JSON.stringify(body); }
    const response = await fetch(path, options); let data = null; try { data = await response.json(); } catch (_) { }
    if (!response.ok) { const error = new Error(data?.errors ? Object.values(data.errors).join("; ") : data?.message || `Yêu cầu thất bại (${response.status})`); error.status = response.status; throw error; }
    return data;
  }
  function apply(change, structural = false) {
    if (!state.layout || state.busy) return;
    if (structural && !state.layout.structureEditable) { message(state.layout.lockReason || "Hãy tạo bản sao để thay đổi bố trí phòng này.", true); return; }
    try { const previous = copy(state.layout), next = change(copy(state.layout)); validate(next); state.undo.push(previous); if (state.undo.length > 40) state.undo.shift(); state.redo = []; state.layout = next; render(); $("message").hidden = true; }
    catch (error) { message(error.message, true); }
  }
  function renderTypes() {
    const previous = $("type").value; $("type").replaceChildren();
    for (const type of state.types) { const option = document.createElement("option"); option.value = type.id; option.textContent = `${type.typeName} · ${type.seatSpan === 2 ? "đôi" : "đơn"}`; $("type").append(option); }
    if (state.types.some((type) => String(type.id) === previous)) $("type").value = previous;
  }
  function sync() {
    const changed = dirty(); $("dirty").hidden = !changed; $("save").disabled = state.busy || !state.layout || !changed;
    $("undo").disabled = state.busy || !state.undo.length; $("redo").disabled = state.busy || !state.redo.length;
    $("copy").disabled = state.busy || !state.layout || changed;
    host.querySelectorAll("[data-sle-structure]").forEach((button) => { button.disabled = state.busy || !state.layout?.structureEditable; });
    const aisleOption = $("status").querySelector('option[value="AISLE"]'); if (aisleOption) aisleOption.disabled = !state.layout?.structureEditable;
    $("selection-count").textContent = `${state.selected.size} ô · ${state.layout ? selectedSeats(state.layout, state.selected).length : 0} ghế`;
  }
  function drawSelection() { $("grid").querySelectorAll("[data-cell]").forEach((node) => { const selected = node.dataset.cells.split(";").some((key) => state.selected.has(key)); node.classList.toggle("is-selected", selected); node.setAttribute("aria-pressed", String(selected)); }); sync(); }
  function render() {
    const layout = state.layout; if (!layout) return;
    $("empty").hidden = true; $("workspace").hidden = false;
    $("title").textContent = `${layout.auditoriumName} · ${layout.cinemaName}`;
    const physical = layout.seats.filter((seat) => seat.status !== "AISLE");
    $("summary").textContent = `${layout.totalRows} hàng × ${layout.totalCols} cột · ${physical.length} ghế · ${physical.reduce((n, seat) => n + Number(seat.seatSpan), 0)} chỗ`;
    $("lock").hidden = layout.structureEditable; $("lock").textContent = layout.lockReason ? `${layout.lockReason} Bạn vẫn có thể chỉnh khoảng cách/kích thước hiển thị và trạng thái ghế; dùng Nhân bản sang phòng mới để thiết kế bố trí khác.` : "";
    $("rows").value = layout.totalRows; $("cols").value = layout.totalCols;
    const settings = layout.settings;
    for (const key of ["seatWidth", "seatHeight", "columnGap", "rowGap"]) { $(key).value = settings[key]; $(`${key}-value`).textContent = `${settings[key]}px`; }
    $("screen").value = settings.screenPosition; $("screen-top").hidden = settings.screenPosition === "BOTTOM"; $("screen-bottom").hidden = settings.screenPosition !== "BOTTOM";
    $("stage").style.zoom = `${state.zoom}%`; host.classList.toggle("sle-previewing", state.preview); $("preview").setAttribute("aria-pressed", String(state.preview)); $("preview").textContent = state.preview ? "Quay lại chỉnh sửa" : "Xem trước";
    const grid = $("grid"); grid.replaceChildren(); grid.style.gridTemplateColumns = `30px repeat(${layout.totalCols}, ${settings.seatWidth}px)`; grid.style.gridTemplateRows = `24px repeat(${layout.totalRows}, ${settings.seatHeight}px)`; grid.style.columnGap = `${settings.columnGap}px`; grid.style.rowGap = `${settings.rowGap}px`;
    const fragment = document.createDocumentFragment();
    const head = (label, x, y, kind, index) => { const button = document.createElement("button"); button.type = "button"; button.className = "sle-axis"; button.textContent = label; button.style.gridColumn = x; button.style.gridRow = y; button.dataset.axis = kind; button.dataset.index = index; button.title = `Chọn cả ${kind === "row" ? "hàng" : "cột"} ${label}`; fragment.append(button); };
    for (let x = 0; x < layout.totalCols; x++) head(x + 1, x + 2, 1, "column", x);
    for (let y = 0; y < layout.totalRows; y++) head(labelForRow(layout, y), 1, y + 2, "row", y);
    const occupied = occupancy(layout);
    for (let y = 0; y < layout.totalRows; y++) for (let x = 0; x < layout.totalCols; x++) {
      const seat = occupied.get(cellKey(x, y)); if (seat && seat.gridX !== x) continue;
      const size = seat ? width(seat) : 1, button = document.createElement("button"); button.type = "button"; button.className = seat ? `sle-seat is-${seat.status.toLowerCase()}` : "sle-cell-empty";
      button.dataset.cell = cellKey(x, y); button.dataset.cells = Array.from({ length: size }, (_, index) => cellKey(x + index, y)).join(";"); button.style.gridColumn = `${x + 2} / span ${size}`; button.style.gridRow = y + 2;
      button.setAttribute("aria-pressed", "false");
      if (seat) { button.style.setProperty("--seat-color", typeColor(seat.seatTypeId)); button.textContent = seat.status === "AISLE" ? "·" : `${seat.rowLabel}${seat.colNumber}`; button.title = `${seat.rowLabel}${seat.colNumber} · ${seat.seatTypeName} · rộng ${size} ô · ${seat.status}`; if (seat.seatSpan === 2) button.classList.add("is-couple"); }
      else { button.textContent = "+"; button.title = `Ô trống ${labelForRow(layout, y)}, cột ${x + 1}`; }
      button.setAttribute("aria-label", button.title); fragment.append(button);
    }
    grid.append(fragment);
    $("legend").replaceChildren();
    for (const type of state.types) { const count = physical.filter((seat) => seat.seatTypeId === type.id).length; if (!count) continue; const item = document.createElement("span"), dot = document.createElement("i"); dot.style.backgroundColor = typeColor(type.id); item.append(dot, document.createTextNode(`${type.typeName}: ${count}`)); $("legend").append(item); }
    drawSelection();
  }
  function rectangle(a, b) {
    const [ax, ay] = a.split(",").map(Number), [bx, by] = b.split(",").map(Number), result = new Set();
    for (let y = Math.min(ay, by); y <= Math.max(ay, by); y++) for (let x = Math.min(ax, bx); x <= Math.max(ax, bx); x++) result.add(cellKey(x, y)); return result;
  }
  function selectCell(key, event) {
    if (event.shiftKey && state.anchor) state.selected = rectangle(state.anchor, key);
    else if (event.ctrlKey || event.metaKey) { state.selected = toggleSelection(state.layout, state.selected, key); state.anchor = key; }
    else { state.selected = new Set([key]); state.anchor = key; }
    state.selected = expandSelection(state.layout, state.selected); drawSelection();
  }
  $("grid").addEventListener("pointerdown", (event) => {
    if (state.preview || !state.layout || event.button !== 0) return;
    const axis = event.target.closest("[data-axis]");
    if (axis) { const result = event.ctrlKey || event.metaKey ? new Set(state.selected) : new Set(), index = Number(axis.dataset.index); if (axis.dataset.axis === "row") for (let x = 0; x < state.layout.totalCols; x++) result.add(cellKey(x, index)); else for (let y = 0; y < state.layout.totalRows; y++) result.add(cellKey(index, y)); state.selected = expandSelection(state.layout, result); state.anchor = null; drawSelection(); return; }
    const button = event.target.closest("[data-cell]"); if (!button) return;
    selectCell(button.dataset.cell, event); state.drag = { start: button.dataset.cell, base: event.ctrlKey || event.metaKey ? new Set(state.selected) : new Set(), moved: false };
    event.preventDefault();
  });
  $("grid").addEventListener("pointerover", (event) => {
    if (!state.drag || !(event.buttons & 1) || state.preview) return;
    const button = event.target.closest("[data-cell]"); if (!button || button.dataset.cell === state.drag.start) return;
    state.drag.moved = true; state.selected = expandSelection(state.layout, new Set([...state.drag.base, ...rectangle(state.drag.start, button.dataset.cell)])); drawSelection();
  });
  document.addEventListener("pointerup", () => { state.drag = null; });
  $("grid").addEventListener("click", (event) => { if (event.detail === 0 && !state.preview) { const axis = event.target.closest("[data-axis]"), button = event.target.closest("[data-cell]"); if (button) selectCell(button.dataset.cell, event); else if (axis) { const chosen = new Set(), n = Number(axis.dataset.index); if (axis.dataset.axis === "row") for (let x = 0; x < state.layout.totalCols; x++) chosen.add(cellKey(x, n)); else for (let y = 0; y < state.layout.totalRows; y++) chosen.add(cellKey(n, y)); state.selected = expandSelection(state.layout, chosen); drawSelection(); } } });
  async function load(roomId, options = {}) {
    if (options.user?.role !== "ADMIN") { clear(); return; }
    state.types = options.types || state.types; state.bridge = options.bridge || state.bridge; host.hidden = false; document.body.classList.add("seat-editor-active");
    const epoch = ++state.epoch;
    state.requestedRoomId = roomId; state.layout = null; state.baseline = ""; state.selected.clear(); state.undo = []; state.redo = []; state.anchor = null; state.drag = null; state.busy = true;
    $("load-retry").hidden = true; $("empty").hidden = false; $("empty").textContent = "Đang mở trình thiết kế…"; $("workspace").hidden = true; sync();
    try { const data = await api(`/api/admin/auditoriums/${roomId}/layout`); if (epoch !== state.epoch) return; data.settings = { ...settingsDefault, ...data.settings }; data.seats.forEach((seat) => { seat.widthUnits ||= seat.seatSpan; }); state.layout = data; state.baseline = signature(data); state.undo = []; state.redo = []; state.selected.clear(); state.anchor = null; $("copy-name").value = `${data.auditoriumName} (bản sao)`.slice(0, 100); renderTypes(); render(); $("message").hidden = true; }
    catch (error) { if (epoch !== state.epoch) return; $("empty").textContent = error.message; $("load-retry").hidden = false; if (error.status === 401) location.replace("/login"); }
    finally { if (epoch === state.epoch) { state.busy = false; sync(); } }
  }
  function clear() { state.epoch++; state.layout = null; state.requestedRoomId = null; state.busy = false; state.selected.clear(); state.undo = []; state.redo = []; state.baseline = ""; $("load-retry").hidden = true; $("workspace").hidden = true; $("empty").hidden = false; $("empty").textContent = "Chọn rạp và phòng ở phía trên để thiết kế sơ đồ ghế."; }
  async function action(name) {
    if (name === "retry-load" && !state.busy && state.requestedRoomId != null) { await load(state.requestedRoomId, { user: { role: "ADMIN" } }); return; }
    if (!state.layout || state.busy) return;
    if (name === "undo" || name === "redo") { const from = name === "undo" ? state.undo : state.redo, to = name === "undo" ? state.redo : state.undo; if (from.length) { to.push(copy(state.layout)); state.layout = from.pop(); state.selected.clear(); render(); } return; }
    if (name === "select-all") { state.selected = new Set(); for (let y = 0; y < state.layout.totalRows; y++) for (let x = 0; x < state.layout.totalCols; x++) state.selected.add(cellKey(x, y)); drawSelection(); return; }
    if (name === "clear-selection") { state.selected.clear(); drawSelection(); return; }
    if (name === "preview") { state.preview = !state.preview; render(); return; }
    if (name === "paint") return apply((layout) => paint(layout, state.selected, state.types.find((type) => type.id === Number($("type").value)), Number($("width").value)), true);
    if (name === "width") return apply((layout) => resizeSeats(layout, state.selected, Number($("width").value)), true);
    if (name === "remove") return apply((layout) => remove(layout, state.selected), true);
    if (name === "status" || name === "aisle") { const status = name === "aisle" ? "AISLE" : $("status").value; const aisleChange = status === "AISLE" || selectedSeats(state.layout, state.selected).some((seat) => seat.status === "AISLE"); return apply((layout) => setStatus(layout, state.selected, status), aisleChange); }
    if (name === "label") { const rows = selectedRows(), label = $("row-label").value.trim().toUpperCase(); if (rows.size !== 1) throw new Error("Chọn đúng một hàng trước khi đổi nhãn."); return apply((layout) => { layout.seats.filter((seat) => rows.has(seat.gridY)).forEach((seat) => { seat.rowLabel = label; }); return layout; }, true); }
    if (name === "renumber") return apply((layout) => renumber(layout, selectedRows(), Number($("number-start").value)), true);
    if (name === "resize") return apply((layout) => { layout.totalRows = Number($("rows").value); layout.totalCols = Number($("cols").value); return layout; }, true);
    if (name === "preset" || name === "empty") { if (state.layout.seats.length && !global.confirm("Thay bố trí hiện tại trong bản nháp? Bạn có thể hoàn tác trước khi lưu.")) return; return apply((layout) => name === "empty" ? { ...layout, seats: [] } : preset(layout, state.types, $("preset").value), true); }
    if (name === "reload") { if (canLeave()) await load(state.layout.auditoriumId, { user: { role: "ADMIN" } }); return; }
    state.busy = true; sync(); const roomId = state.layout.auditoriumId, epoch = state.epoch;
    try {
      if (name === "save") { validate(state.layout); const saved = await api(`/api/admin/auditoriums/${roomId}/layout`, "PUT", payload(state.layout)); if (epoch !== state.epoch) return; saved.settings = { ...settingsDefault, ...saved.settings }; state.layout = saved; state.baseline = signature(saved); state.undo = []; state.redo = []; state.selected.clear(); render(); message("Đã lưu sơ đồ. Kích thước, khoảng cách và loại ghế sẽ hiển thị nhất quán ở các trang xem phòng."); await state.bridge?.refreshMap?.(saved); }
      if (name === "copy") { if (dirty()) throw new Error("Lưu sơ đồ hoặc tải lại trước khi tạo bản sao."); const name = $("copy-name").value.trim(); if (!name || name.length > 100) throw new Error("Tên phòng mới cần từ 1–100 ký tự."); const created = await api(`/api/admin/auditoriums/${roomId}/copy-layout`, "POST", { cinemaId: state.layout.cinemaId, name }); if (epoch !== state.epoch) return; state.baseline = signature(state.layout); await state.bridge?.selectRoom?.(created.auditoriumId); message("Đã tạo phòng bản sao. Bạn có thể tự do thay đổi bố trí trong phòng mới."); }
      if (name === "new-type") { const typeName = $("new-type").value.trim().toUpperCase(), defaultSurcharge = Number($("new-surcharge").value); if (!/^[A-Z][A-Z0-9_]{0,49}$/.test(typeName) || !Number.isFinite(defaultSurcharge) || defaultSurcharge < 0) throw new Error("Tên loại dùng A–Z, số hoặc dấu gạch dưới; phụ thu không âm."); const created = await api("/api/seat-types", "POST", { typeName, defaultSurcharge, seatSpan: Number($("new-span").value) }); state.types.push(created); renderTypes(); $("type").value = created.id; $("width").value = created.seatSpan; await state.bridge?.refreshTypes?.(); message(`Đã tạo loại ${created.typeName}. Chọn vùng ghế rồi bấm Đặt / đổi loại.`); }
    } catch (error) { if (epoch === state.epoch) message(error.status === 409 ? `${error.message} Nếu sơ đồ vừa được người khác cập nhật, hãy bấm Tải lại.` : error.message, true); }
    finally { state.busy = false; sync(); }
  }
  host.addEventListener("click", (event) => { const button = event.target.closest("[data-sle-action]"); if (button) void action(button.dataset.sleAction).catch((error) => message(error.message, true)); });
  host.querySelectorAll("[data-sle-setting]").forEach((input) => input.addEventListener("change", () => apply((layout) => { layout.settings[input.dataset.sleSetting] = Number(input.value); return layout; })));
  $("screen").addEventListener("change", () => apply((layout) => { layout.settings.screenPosition = $("screen").value; return layout; }));
  $("zoom").addEventListener("change", () => { state.zoom = Number($("zoom").value); if (state.layout) render(); });
  $("type").addEventListener("change", () => { const type = state.types.find((entry) => entry.id === Number($("type").value)); if (type) $("width").value = type.seatSpan; });
  document.addEventListener("keydown", (event) => { if (!state.layout || host.closest("[hidden]") || /INPUT|SELECT|TEXTAREA/.test(event.target.tagName)) return; if ((event.ctrlKey || event.metaKey) && ["z", "y"].includes(event.key.toLowerCase())) { event.preventDefault(); void action(event.key.toLowerCase() === "y" || event.shiftKey ? "redo" : "undo"); } else if (event.key === "Delete" && state.selected.size) { event.preventDefault(); void action("remove"); } else if (event.key === "Escape") { state.selected.clear(); drawSelection(); } });
  global.addEventListener("beforeunload", (event) => { if (dirty()) { event.preventDefault(); event.returnValue = ""; } });
  global.BbcinemaSeatEditor = { load, clear, canLeave, isDirty: dirty, refreshTypes: (types) => { state.types = types; renderTypes(); if (state.layout) render(); } };
})(typeof window === "undefined" ? globalThis : window);
