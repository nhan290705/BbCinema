"use strict";

((global) => {
  const MAX_POSTER_BYTES = 5 * 1024 * 1024;
  const youtubeHosts = new Set(["youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtube-nocookie.com", "www.youtube-nocookie.com"]);
  function httpUrl(value) {
    if (!value || /\s/.test(value.trim())) return null;
    try { const url = new URL(value.trim()); return ["http:", "https:"].includes(url.protocol) && !url.username && !url.password ? url.href : null; }
    catch (_) { return null; }
  }
  function posterUrl(value) {
    if (/^\/demo\/posters\/[a-z0-9-]+\.svg$/.test(value || "") || /^\/uploads\/posters\/[a-f0-9]{32}\.(?:jpg|png)$/.test(value || "")) return value;
    return httpUrl(value);
  }
  function trailer(value) {
    const valid = httpUrl(value); if (!valid) return null;
    const url = new URL(valid), host = url.hostname.toLowerCase(); let id;
    if (["youtu.be", "www.youtu.be"].includes(host)) id = url.pathname.slice(1);
    else if (youtubeHosts.has(host)) {
      if (url.pathname === "/watch") id = url.searchParams.get("v");
      else if (/^\/(embed|shorts|live)\/[^/]+$/.test(url.pathname)) id = url.pathname.split("/").pop();
    } else return { kind: /\.(mp4|webm)$/i.test(url.pathname) ? "video" : "link", url: valid };
    if (!/^[A-Za-z0-9_-]{11}$/.test(id || "")) return null;
    return { kind: "youtube", url: `https://www.youtube.com/watch?v=${id}`, embedUrl: `https://www.youtube.com/embed/${id}?playsinline=1&rel=0` };
  }
  function validatePoster(file) {
    if (!file || !file.size) throw new Error("Hãy chọn một ảnh poster JPG hoặc PNG.");
    if (!["image/jpeg", "image/png"].includes(file.type)) throw new Error("Poster chỉ nhận ảnh JPG hoặc PNG.");
    if (file.size > MAX_POSTER_BYTES) throw new Error("Poster tối đa 5 MB.");
    return file;
  }
  function renderTrailer(container, value, title = "Trailer phim", immediate = false) {
    container.replaceChildren();
    const media = trailer(value); if (!media) return false;
    const link = document.createElement("a"); link.className = "trailer-external"; link.textContent = "Mở trailer trong tab mới ↗"; link.href = media.url; link.target = "_blank"; link.rel = "noopener noreferrer";
    if (media.kind === "link") { container.append(link); return true; }
    const stage = document.createElement("div"); stage.className = "trailer-stage";
    const play = () => {
      const player = document.createElement(media.kind === "youtube" ? "iframe" : "video");
      if (media.kind === "youtube") {
        player.src = media.embedUrl; player.title = title; player.allow = "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share";
        player.referrerPolicy = "strict-origin-when-cross-origin"; player.allowFullscreen = true;
      } else {
        player.src = media.url; player.controls = true; player.preload = "metadata"; player.setAttribute("aria-label", title);
        player.addEventListener("error", () => { stage.replaceChildren(); const hint = document.createElement("p"); hint.textContent = "Không phát được video này. Hãy thử mở trailer trong tab mới."; stage.append(hint); });
      }
      stage.replaceChildren(player);
    };
    if (immediate) play();
    else { const button = document.createElement("button"); button.type = "button"; button.className = "trailer-play"; button.textContent = "▶ Phát trailer"; button.addEventListener("click", play); stage.append(button); }
    container.append(stage, link); return true;
  }
  global.BbcinemaMovieMedia = { MAX_POSTER_BYTES, httpUrl, posterUrl, trailer, validatePoster, renderTrailer };
})(typeof window === "undefined" ? globalThis : window);
