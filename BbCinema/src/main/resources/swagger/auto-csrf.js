async function swaggerCsrfInterceptor(request) {
    const endpoint = new URL(
        __CSRF_ENDPOINT__,
        window.location.origin
    );

    const target = new URL(
        request.url,
        window.location.href
    );

    // Chỉ gửi token cho backend cùng origin.
    if (target.origin !== endpoint.origin) {
        return request;
    }

    request.credentials = "same-origin";

    const method = (request.method || "GET").toUpperCase();

    // Các yêu cầu đọc dữ liệu không cần lấy CSRF.
    if (["GET", "HEAD", "OPTIONS", "TRACE"].includes(method)) {
        return request;
    }

    // Lấy token mới trước khi gửi POST/PUT/PATCH/DELETE.
    const response = await fetch(endpoint.href, {
        method: "GET",
        credentials: "same-origin",
        cache: "no-store",
        headers: {
            Accept: "application/json"
        }
    });

    if (!response.ok) {
        throw new Error(
            "Không lấy được CSRF token: HTTP " + response.status
        );
    }

    const csrf = await response.json();

    if (!csrf.headerName || !csrf.token) {
        throw new Error("API CSRF trả dữ liệu không hợp lệ");
    }

    request.headers = request.headers || {};

    // Xóa token cũ nếu request đã chứa header này.
    for (const name of Object.keys(request.headers)) {
        if (name.toLowerCase() === csrf.headerName.toLowerCase()) {
            delete request.headers[name];
        }
    }

    // Gắn token mới vào request ban đầu.
    request.headers[csrf.headerName] = csrf.token;

    return request;
}