"use strict";

(() => {
  const $ = (id) => document.getElementById(id);
  if (document.body.dataset.page !== "management") return;
  const media = window.BbcinemaMovieMedia;
  const state = { user: null, genres: [], page: 0, totalPages: 0, movieEpoch: 0, authEpoch: 0, editEpoch: 0, editId: null, genreEditId: null, posterFile: null, posterObjectUrl: null, saving: false, loadingMovie: false };
  const statuses = { COMING_SOON: "Sắp chiếu", NOW_SHOWING: "Đang chiếu", ENDED: "Đã kết thúc", HIDDEN: "Ẩn" };

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }

  function notify(message, error = false) {
    $("message").textContent = message;
    $("message").className = error ? "message error" : "message";
    $("message").hidden = false;
  }

  function safePosterUrl(value) {
    return media.posterUrl(value);
  }

  async function api(path, method = "GET", body) {
    const options = { method, credentials: "same-origin", headers: { Accept: "application/json" } };
    if (method !== "GET") {
      const csrf = await api("/api/auth/csrf");
      options.headers[csrf.headerName] = csrf.token;
      if (body instanceof FormData) options.body = body;
      else if (body !== undefined) { options.headers["Content-Type"] = "application/json"; options.body = JSON.stringify(body); }
    }
    const response = await fetch(path, options);
    let result = null;
    if (response.status !== 204) {
      try { result = await response.json(); } catch (_) { /* Preserve the HTTP error if a proxy returns non-JSON. */ }
    }
    if (!response.ok) {
      const details = result?.errors ? Object.values(result.errors).join("; ") : "";
      const error = new Error(details || result?.message || `Yêu cầu thất bại (${response.status}).`);
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
      notify(error.message || "Không thể kết nối máy chủ. Vui lòng thử lại.", true);
    } finally { if (button?.isConnected) button.disabled = false; }
  }

  function bindForm(id, action) {
    $(id).addEventListener("submit", (event) => {
      event.preventDefault();
      void run(() => action(event.currentTarget), event.submitter);
    });
  }

  function poster(movie) {
    const box = element("div", "poster");
    const fallback = element("span", "poster-fallback", "BBCINEMA\nCHƯA CÓ POSTER");
    box.append(fallback);
    const url = safePosterUrl(movie.posterUrl);
    if (url) {
      const image = element("img"); image.alt = `Poster ${movie.title}`; image.loading = "lazy";
      image.referrerPolicy = "no-referrer";
      image.addEventListener("error", () => { image.hidden = true; fallback.hidden = false; });
      image.addEventListener("load", () => { fallback.hidden = true; });
      image.src = url; box.append(image);
    }
    return box;
  }

  function movieTags(movie) {
    const tags = element("div", "tags");
    const status = Object.hasOwn(statuses, movie.status) ? movie.status : "ENDED";
    tags.append(element("span", `tag status-${status}`, statuses[status]), element("span", "tag age-tag", movie.ageRating));
    return tags;
  }

  function movieCard(movie) {
    const card = element("article", "movie-card");
    card.append(poster(movie));
    const content = element("div", "movie-content");
    content.append(movieTags(movie));
    const title = element("h3");
    title.textContent = movie.title;
    content.append(title, element("p", "movie-meta", `${movie.durationMinutes} phút${movie.releaseDate ? ` · ${formatDate(movie.releaseDate)}` : ""}`));
    content.append(element("p", "genre-text", (movie.genres || []).map((genre) => genre.genreName).join(" · ") || "Chưa phân loại"));
    const edit = element("button", "secondary", "Chỉnh sửa phim"); edit.type = "button";
    edit.addEventListener("click", () => void run(() => editMovie(movie.id), edit)); content.append(edit);
    card.append(content); return card;
  }

  function formatDate(value) {
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value || "");
    return match ? `${match[3]}/${match[2]}/${match[1]}` : value;
  }

  async function loadMovies(requestedPage = 0) {
    const epoch = ++state.movieEpoch;
    const params = new URLSearchParams({ page: String(requestedPage), size: "12" });
    for (const key of ["title", "genreId", "status"]) {
      const value = $("filter-form").elements[key].value.trim();
      if (value) params.set(key, value);
    }
    $("movie-list").replaceChildren(); $("movie-list").setAttribute("aria-busy", "true");
    $("list-empty").textContent = "Đang tải danh sách phim…"; $("list-empty").hidden = false;
    $("previous-page").disabled = true; $("next-page").disabled = true;
    try {
      const result = await api(`/api/admin/movies?${params}`);
      if (epoch !== state.movieEpoch) return;
      state.page = result.page; state.totalPages = result.totalPages;
      const movies = result.items || [];
      const fragment = document.createDocumentFragment();
      for (const movie of movies) fragment.append(movieCard(movie));
      $("movie-list").replaceChildren(fragment);
      $("list-empty").hidden = movies.length > 0;
      $("list-empty").textContent = "Không tìm thấy phim phù hợp. Hãy thử tên hoặc bộ lọc khác.";
      $("result-count").textContent = `${result.totalElements} phim`;
      $("page-label").textContent = `Trang ${result.totalPages ? result.page + 1 : 0} / ${result.totalPages}`;
      $("previous-page").disabled = result.page <= 0;
      $("next-page").disabled = result.page + 1 >= result.totalPages;
    } catch (error) {
      if (epoch !== state.movieEpoch) return;
      $("list-empty").textContent = "Không tải được danh sách phim. Bấm tìm phim để thử lại.";
      $("result-count").textContent = "";
      throw error;
    } finally {
      if (epoch === state.movieEpoch) $("movie-list").setAttribute("aria-busy", "false");
    }
  }

  async function loadGenres() {
    const epoch = state.authEpoch;
    const genres = await api("/api/genres");
    if (epoch !== state.authEpoch) return;
    state.genres = genres;
    const selected = $("genre-filter").value;
    $("genre-filter").replaceChildren();
    const all = element("option", "", "Tất cả thể loại"); all.value = ""; $("genre-filter").append(all);
    for (const genre of genres) { const option = element("option", "", genre.genreName); option.value = genre.id; $("genre-filter").append(option); }
    $("genre-filter").value = genres.some((genre) => String(genre.id) === selected) ? selected : "";
    renderGenres();
  }

  function selectedGenreIds() {
    return Array.from($("genre-checkboxes").querySelectorAll("input:checked"), (input) => Number(input.value));
  }

  function renderGenres() {
    const chosen = new Set(selectedGenreIds());
    $("genre-checkboxes").replaceChildren(); $("genre-list").replaceChildren();
    $("no-genres").hidden = state.genres.length > 0;
    for (const genre of state.genres) {
      const label = element("label"); const checkbox = element("input"); checkbox.type = "checkbox"; checkbox.name = "genreIds"; checkbox.value = genre.id; checkbox.checked = chosen.has(genre.id);
      label.append(checkbox, element("span", "", genre.genreName)); $("genre-checkboxes").append(label);
      const row = element("li"); row.append(element("span", "", genre.genreName));
      const actions = element("div", "genre-actions");
      const edit = element("button", "secondary", "Sửa"); edit.type = "button";
      edit.addEventListener("click", () => { state.genreEditId = genre.id; $("genre-form").elements.genreName.value = genre.genreName; $("save-genre").textContent = "Lưu thể loại"; $("genre-form").elements.genreName.focus(); });
      const remove = element("button", "secondary danger", "Xóa"); remove.type = "button";
      remove.addEventListener("click", () => void run(async () => {
        await api(`/api/admin/genres/${genre.id}`, "DELETE");
        if (state.genreEditId === genre.id) resetGenre();
        await loadGenres(); await loadMovies(0); notify(`Đã xóa thể loại ${genre.genreName}.`);
      }, remove));
      actions.append(edit, remove); row.append(actions); $("genre-list").append(row);
    }
  }

  function resetGenre() { state.genreEditId = null; $("genre-form").reset(); $("save-genre").textContent = "Thêm thể loại"; }

  function clearPosterFile(resetInput = true) {
    if (state.posterObjectUrl) URL.revokeObjectURL(state.posterObjectUrl);
    state.posterObjectUrl = null; state.posterFile = null;
    if (resetInput) $("poster-file").value = "";
    $("poster-url").disabled = false; $("poster-cancel").hidden = true;
  }

  function refreshPosterPreview() {
    const image = $("poster-preview"), url = state.posterObjectUrl || safePosterUrl($("poster-url").value.trim());
    image.hidden = !url; $("poster-placeholder").hidden = !!url;
    if (url) image.src = url; else image.removeAttribute("src");
    $("poster-file-info").textContent = state.posterFile ? `${state.posterFile.name} · ${(state.posterFile.size / 1024 / 1024).toFixed(2)} MB · Chưa tải lên` : "JPG hoặc PNG, tối đa 5 MB. Ảnh được tải lên khi bạn lưu phim.";
    $("poster-remove").disabled = !url;
  }

  function clearMediaPreview() {
    clearPosterFile(); refreshPosterPreview(); $("trailer-preview").replaceChildren(); $("trailer-preview").hidden = true;
  }

  function setMovieSaving(value) {
    state.saving = value;
    $("movie-form").querySelectorAll("input,select,textarea,button").forEach((node) => { node.disabled = value; });
    $("new-movie").disabled = value;
    if (!value) { $("poster-url").disabled = !!state.posterFile; refreshPosterPreview(); }
  }

  function resetMovie() {
    if (state.saving) return;
    state.editEpoch++; state.editId = null; state.loadingMovie = false; $("movie-form").reset();
    clearMediaPreview();
    $("editor-title").textContent = "Thêm phim mới"; $("save-movie").textContent = "Lưu phim mới";
    $("movie-form").closest(".editor-card").classList.remove("editing");
    $("movie-form").closest(".editor-card").setAttribute("aria-busy", "false");
  }

  async function editMovie(id) {
    if (state.saving) throw new Error("Phim đang được lưu. Vui lòng chờ hoàn tất trước khi chuyển phim.");
    const epoch = ++state.editEpoch;
    state.loadingMovie = true;
    const card = $("movie-form").closest(".editor-card"); card.setAttribute("aria-busy", "true");
    try {
      const movie = await api(`/api/admin/movies/${id}`);
      if (epoch !== state.editEpoch) return;
      state.editId = movie.id;
      const form = $("movie-form");
      for (const key of ["title", "description", "durationMinutes", "ageRating", "castList", "director", "posterUrl", "trailerUrl", "releaseDate", "status"]) form.elements[key].value = movie[key] ?? "";
      clearMediaPreview();
      const genreIds = new Set((movie.genres || []).map((genre) => String(genre.id)));
      $("genre-checkboxes").querySelectorAll("input").forEach((input) => { input.checked = genreIds.has(input.value); });
      $("editor-title").textContent = `Sửa phim #${movie.id}`; $("save-movie").textContent = "Lưu thay đổi phim";
      card.classList.add("editing"); card.scrollIntoView({ behavior: "smooth", block: "start" });
      notify(`Đang chỉnh sửa: ${movie.title}`);
    } catch (error) { if (epoch === state.editEpoch) throw error; }
    finally { if (epoch === state.editEpoch) { state.loadingMovie = false; card.setAttribute("aria-busy", "false"); } }
  }

  function setUser(user) {
    if (!user && document.body.classList.contains("admin-ready")) { window.location.replace("/login"); return; }
    state.user = user; state.authEpoch++; state.movieEpoch++; state.editEpoch++; state.loadingMovie = false;
    $("login-panel").hidden = !!user; $("logout").hidden = !user;
    $("management-workspace").hidden = user?.role !== "ADMIN";
    $("account-label").textContent = user ? `${user.fullName} · ${user.role}` : "";
    if (!user || user.role !== "ADMIN") {
      state.editId = null; state.genreEditId = null; state.genres = [];
      $("movie-list").replaceChildren(); $("genre-checkboxes").replaceChildren(); $("genre-list").replaceChildren();
      $("movie-form").reset(); $("genre-form").reset(); $("login-form").elements.password.value = "";
      clearMediaPreview();
    }
  }

  async function openManagement(user) {
    setUser(user);
    if (user.role !== "ADMIN") { notify("Tài khoản này không có quyền quản lý phim.", true); return; }
    resetMovie(); resetGenre();
    await loadGenres();
    if (state.user?.role === "ADMIN") await loadMovies(0);
  }

  bindForm("filter-form", () => loadMovies(0));
  $("previous-page").addEventListener("click", () => void run(() => loadMovies(Math.max(0, state.page - 1))));
  $("next-page").addEventListener("click", () => void run(() => loadMovies(state.page + 1)));

  bindForm("login-form", async (form) => {
    const data = Object.fromEntries(new FormData(form));
    const user = await api("/api/auth/login", "POST", data); form.elements.password.value = "";
    await openManagement(user);
  });
  $("logout").addEventListener("click", () => void run(async () => { await api("/api/auth/logout", "POST"); setUser(null); notify("Đã đăng xuất."); }, $("logout")));
  $("new-movie").addEventListener("click", resetMovie);
  $("new-genre").addEventListener("click", resetGenre);
  $("poster-file").addEventListener("change", () => {
    const file = $("poster-file").files[0];
    if (!file) return;
    try {
      media.validatePoster(file);
      clearPosterFile(false); state.posterFile = file; state.posterObjectUrl = URL.createObjectURL(file);
      $("poster-url").disabled = true; $("poster-cancel").hidden = false; refreshPosterPreview();
    } catch (error) { $("poster-file").value = ""; notify(error.message, true); }
  });
  $("poster-cancel").addEventListener("click", () => { clearPosterFile(); refreshPosterPreview(); });
  $("poster-remove").addEventListener("click", () => { clearPosterFile(); $("poster-url").value = ""; refreshPosterPreview(); });
  $("poster-url").addEventListener("input", refreshPosterPreview);
  $("poster-preview").addEventListener("error", () => { $("poster-preview").hidden = true; $("poster-placeholder").hidden = false; });
  $("preview-trailer").addEventListener("click", () => {
    const value = $("trailer-url").value.trim();
    if (!media.trailer(value)) { notify("Hãy nhập liên kết video YouTube hoặc URL HTTP/HTTPS hợp lệ.", true); return; }
    media.renderTrailer($("trailer-preview"), value, "Xem trước trailer", true); $("trailer-preview").hidden = false;
  });
  $("trailer-url").addEventListener("input", () => { $("trailer-preview").replaceChildren(); $("trailer-preview").hidden = true; });
  window.addEventListener("beforeunload", () => { if (state.posterObjectUrl) URL.revokeObjectURL(state.posterObjectUrl); });
  bindForm("movie-form", async (form) => {
    if (state.saving) return;
    if (state.loadingMovie) throw new Error("Đang tải thông tin phim. Vui lòng chờ trước khi lưu.");
    const data = Object.fromEntries(new FormData(form)); const genreIds = selectedGenreIds();
    if (!genreIds.length) throw new Error("Vui lòng chọn ít nhất một thể loại cho phim.");
    if (!state.posterFile && data.posterUrl && !safePosterUrl(data.posterUrl.trim())) throw new Error("Hãy chọn ảnh poster hoặc nhập URL ảnh hợp lệ.");
    const trailer = data.trailerUrl?.trim() ? media.trailer(data.trailerUrl) : null;
    if (data.trailerUrl?.trim() && !trailer) throw new Error("Trailer cần liên kết HTTP/HTTPS hợp lệ; với YouTube hãy chọn liên kết của một video cụ thể.");
    const body = { ...data, durationMinutes: Number(data.durationMinutes), genreIds };
    for (const key of ["description", "director", "castList", "posterUrl", "trailerUrl", "releaseDate"]) body[key] = body[key]?.trim() || null;
    body.trailerUrl = trailer?.url || null;
    let request = body;
    if (state.posterFile) {
      media.validatePoster(state.posterFile); request = new FormData();
      request.append("movie", new Blob([JSON.stringify(body)], { type: "application/json" }), "movie.json");
      request.append("poster", state.posterFile, state.posterFile.name);
    }
    const id = state.editId; const editEpoch = state.editEpoch; const authEpoch = state.authEpoch;
    setMovieSaving(true);
    try {
      const saved = await api(`/api/admin/movies${id ? `/${id}` : ""}`, id ? "PUT" : "POST", request);
      if (authEpoch !== state.authEpoch) return;
      if (editEpoch === state.editEpoch) {
        state.editId = saved.id; $("editor-title").textContent = `Sửa phim #${saved.id}`; $("save-movie").textContent = "Lưu thay đổi phim";
        form.elements.posterUrl.value = saved.posterUrl || ""; form.elements.trailerUrl.value = saved.trailerUrl || "";
        clearPosterFile(); refreshPosterPreview(); form.closest(".editor-card").classList.add("editing");
      }
      await loadMovies(0); notify(id ? "Đã cập nhật phim và hình ảnh." : "Đã thêm phim mới và lưu hình ảnh.");
    } finally { setMovieSaving(false); }
  });
  bindForm("genre-form", async (form) => {
    const id = state.genreEditId; const authEpoch = state.authEpoch;
    await api(`/api/admin/genres${id ? `/${id}` : ""}`, id ? "PUT" : "POST", { genreName: form.elements.genreName.value });
    if (authEpoch !== state.authEpoch) return;
    if (id === state.genreEditId) resetGenre();
    await loadGenres(); await loadMovies(0); notify(id ? "Đã cập nhật thể loại." : "Đã thêm thể loại.");
  });

  void run(async () => {
    const epoch = state.authEpoch;
    let user;
    try { user = await api("/api/users/me"); }
    catch (error) { if (epoch !== state.authEpoch) return; if (error.status === 401) { setUser(null); return; } throw error; }
    if (epoch === state.authEpoch) await openManagement(user);
  });
})();
