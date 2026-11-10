"use strict";

(() => {
  const form = document.getElementById("auth-form");
  if (!form) return;

  const register = document.body.dataset.authPage === "register";
  const message = document.getElementById("form-message");
  const submit = document.getElementById("submit-button");
  const buttonLabel = submit.querySelector("[data-button-label]");
  const defaultLabel = buttonLabel?.textContent || (register ? "Tạo tài khoản" : "Đăng nhập");
  const fieldNames = register ? ["fullName", "email", "phone", "password", "confirmPassword"] : ["email", "password"];
  const allowedDestinations = new Set(["/admin.html", "/movie-management.html", "/cinema-management.html", "/showtime-management.html"]);
  let busy = false;

  function input(name) { return form.elements.namedItem(name); }

  function fieldError(name, text = "") {
    const control = input(name);
    const error = document.getElementById(`${name}-error`);
    const wrapper = control?.closest("[data-field]");
    if (!control || !error) return;
    control.setAttribute("aria-invalid", text ? "true" : "false");
    error.textContent = text;
    error.hidden = !text;
    wrapper?.classList.toggle("has-error", !!text);
    if (wrapper) wrapper.dataset.invalid = String(!!text);
  }

  function showMessage(text, kind = "error") {
    if (!message) return;
    message.textContent = text;
    message.dataset.kind = kind;
    message.classList.toggle("is-error", kind === "error");
    message.classList.toggle("is-success", kind === "success");
    message.setAttribute("role", kind === "error" ? "alert" : "status");
    message.setAttribute("aria-live", kind === "error" ? "assertive" : "polite");
    message.hidden = false;
  }

  function clearMessage() {
    if (!message) return;
    message.hidden = true;
    message.textContent = "";
  }

  function setBusy(value) {
    busy = value;
    submit.disabled = value;
    submit.classList.toggle("is-loading", value);
    form.setAttribute("aria-busy", String(value));
    if (buttonLabel) buttonLabel.textContent = value ? (register ? "Đang tạo tài khoản…" : "Đang đăng nhập…") : defaultLabel;
  }

  function passwordBytes(value) { return new TextEncoder().encode(value).length; }

  function updatePasswordHints() {
    if (!register) return;
    const password = input("password")?.value || "";
    const confirm = input("confirmPassword")?.value || "";
    const states = {
      length: password.length >= 8 && passwordBytes(password) <= 72 && !!password.trim(),
      match: !!password && !!confirm && password === confirm
    };
    document.querySelectorAll("[data-password-rule]").forEach((rule) => {
      const valid = states[rule.dataset.passwordRule] === true;
      rule.dataset.valid = String(valid);
      rule.classList.toggle("is-valid", valid);
    });
    const strength = document.getElementById("password-strength");
    if (strength) {
      strength.textContent = !password ? "" : states.length ? "Mật khẩu đáp ứng yêu cầu" : "Mật khẩu cần ít nhất 8 ký tự";
      strength.dataset.strength = states.length ? "ready" : password ? "short" : "empty";
      if (passwordBytes(password) > 72) strength.textContent = "Mật khẩu quá dài. Hãy dùng tối đa 72 byte UTF-8.";
    }
  }

  function validate() {
    const data = {};
    const errors = {};
    for (const name of fieldNames) {
      const value = input(name)?.value || "";
      data[name] = ["password", "confirmPassword"].includes(name) ? value : value.trim();
      fieldError(name);
    }
    data.email = data.email.toLowerCase();
    if (!data.email) errors.email = "Bạn chưa nhập email.";
    else if (data.email.length > 120 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(data.email)) errors.email = "Vui lòng nhập địa chỉ email hợp lệ.";
    if (register) {
      if (!data.fullName) errors.fullName = "Bạn chưa nhập họ và tên.";
      else if (data.fullName.length > 100) errors.fullName = "Họ và tên tối đa 100 ký tự.";
      if (!/^0[0-9]{9}$/.test(data.phone)) errors.phone = "Nhập 10 chữ số, bắt đầu bằng 0.";
    }
    if (!data.password.trim()) errors.password = "Bạn chưa nhập mật khẩu.";
    else if (register && data.password.length < 8) errors.password = "Mật khẩu cần ít nhất 8 ký tự.";
    else if (passwordBytes(data.password) > 72) errors.password = "Mật khẩu quá dài. Hãy dùng tối đa 72 byte UTF-8.";
    if (register) {
      if (!data.confirmPassword.trim()) errors.confirmPassword = "Nhập lại mật khẩu để xác nhận.";
      else if (data.confirmPassword !== data.password) errors.confirmPassword = "Mật khẩu xác nhận chưa khớp.";
    }
    for (const [name, text] of Object.entries(errors)) fieldError(name, text);
    const first = fieldNames.find((name) => errors[name]);
    if (first) input(first)?.focus();
    return first ? null : data;
  }

  async function request(path, options = {}) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 20000);
    try {
      const response = await fetch(path, { ...options, credentials: "same-origin", signal: controller.signal,
        headers: { Accept: "application/json", ...options.headers } });
      let result = null;
      try { result = await response.json(); } catch (_) { /* Preserve a readable HTTP error if a proxy returns HTML. */ }
      if (!response.ok) {
        const error = new Error(result?.message || "Không thể hoàn tất yêu cầu. Vui lòng thử lại.");
        error.status = response.status;
        error.fields = result?.errors;
        throw error;
      }
      return result;
    } catch (error) {
      if (error.name === "AbortError") throw new Error("Kết nối mất nhiều thời gian. Vui lòng thử lại.");
      if (typeof error.status !== "number") throw new Error("Không thể kết nối Bbcinema. Vui lòng kiểm tra kết nối và thử lại.");
      throw error;
    } finally { clearTimeout(timeout); }
  }

  async function post(path, body) {
    const csrf = await request("/api/auth/csrf");
    if (!csrf?.headerName || !csrf?.token) throw new Error("Không lấy được phiên làm việc. Vui lòng tải lại trang.");
    return request(path, { method: "POST", headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token }, body: JSON.stringify(body) });
  }

  function safeDestination(value) {
    if (typeof value !== "string" || !value) return null;
    try {
      const destination = new URL(value, window.location.origin);
      if (destination.origin === window.location.origin && allowedDestinations.has(destination.pathname)
          && !destination.username && !destination.password) return destination.pathname;
    } catch (_) { /* Stay on the form if the server supplied an unexpected path. */ }
    return null;
  }

  function handleError(error) {
    let first = null;
    if (error.fields && typeof error.fields === "object") {
      for (const name of fieldNames) {
        const text = error.fields[name];
        if (typeof text === "string") {
          fieldError(name, text);
          if (!first) first = name;
        }
      }
      if (error.fields.passwordWithinByteLimit && !error.fields.password) {
        fieldError("password", "Mật khẩu quá dài. Hãy dùng tối đa 72 byte UTF-8.");
        first ||= "password";
      }
    }
    let text = error.message;
    if (error.status === 401) text = "Email hoặc mật khẩu chưa đúng, hoặc tài khoản đang bị khóa. Bạn thử lại nhé.";
    if (error.status === 403) text = "Phiên làm việc đã thay đổi. Vui lòng thử lại để lấy phiên mới.";
    if (error.status >= 500) text = "Bbcinema đang bận một chút. Vui lòng thử lại sau.";
    showMessage(text);
    if (first) input(first)?.focus();
  }

  for (const name of fieldNames) {
    input(name)?.addEventListener("input", () => {
      fieldError(name);
      if (name === "password" && register) fieldError("confirmPassword");
      if (message?.dataset.kind === "error") clearMessage();
      updatePasswordHints();
    });
  }

  document.querySelectorAll("[data-toggle-password]").forEach((button) => {
    button.addEventListener("click", (event) => {
      event.preventDefault();
      const control = document.getElementById(button.dataset.togglePassword);
      if (!control) return;
      const visible = control.type === "password";
      control.type = visible ? "text" : "password";
      button.setAttribute("aria-pressed", String(visible));
      button.setAttribute("aria-label", visible ? "Ẩn mật khẩu" : "Hiện mật khẩu");
      const open = button.querySelector("[data-eye-open]");
      const closed = button.querySelector("[data-eye-closed]");
      if (open) { open.hidden = visible; open.toggleAttribute?.("hidden", visible); }
      if (closed) { closed.hidden = !visible; closed.toggleAttribute?.("hidden", !visible); }
    });
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (busy) return;
    clearMessage();
    const body = validate();
    if (!body) return;
    setBusy(true);
    try {
      const result = await post(register ? "/api/auth/register" : "/api/auth/login", body);
      // Passwords remain in memory only for this submission; never write them to browser storage.
      input("password").value = "";
      if (register) {
        input("confirmPassword").value = "";
        try { window.sessionStorage.setItem("bbcinema.registeredEmail", body.email); } catch (_) { /* Registration also works when browser storage is unavailable. */ }
        window.location.assign("/login?registered=1");
      } else {
        const destination = safeDestination(result?.redirectUrl);
        if (destination) window.location.assign(destination);
        else showMessage("Đăng nhập thành công. Giao diện cho vai trò này chưa được thiết lập.", "success");
      }
    } catch (error) { handleError(error); }
    finally { setBusy(false); }
  });

  if (!register && new URLSearchParams(window.location.search).get("registered") === "1") {
    showMessage("Tài khoản đã sẵn sàng. Đăng nhập để bắt đầu trải nghiệm cùng Bbcinema.", "success");
    try {
      const email = window.sessionStorage.getItem("bbcinema.registeredEmail");
      if (email) input("email").value = email;
      window.sessionStorage.removeItem("bbcinema.registeredEmail");
    } catch (_) { /* A success message does not depend on browser storage. */ }
  }
  updatePasswordHints();
})();
