"use strict";

const categories = {
    all: "Top stories", world: "World", technology: "Technology", science: "Science",
    business: "Business", culture: "Culture", other: "Other"
};
const demoArt = new Map([
    ["/demo/coastal-grid", "/images/coastal-grid.jpg"],
    ["/demo/a-calmer-web", "/images/calmer-web.jpg"],
    ["/demo/listening-to-glaciers", "/images/glacier.jpg"]
]);
const ui = {
    categoryButtons: document.querySelectorAll("[data-category]"),
    modeButtons: document.querySelectorAll("[data-mode]"),
    searchForm: document.querySelector("#search-form"),
    search: document.querySelector("#search-input"),
    source: document.querySelector("#source-filter"),
    sort: document.querySelector("#sort-order"),
    reset: document.querySelector("#reset-filters"),
    refresh: document.querySelector("#refresh-feed"),
    refreshLabel: document.querySelector("#refresh-label"),
    storiesTitle: document.querySelector("#stories-title"),
    storyCount: document.querySelector("#story-count"),
    editionNote: document.querySelector("#edition-note"),
    stories: document.querySelector("#story-region"),
    sources: document.querySelector("#source-list"),
    sourceNote: document.querySelector("#source-note"),
    notice: document.querySelector("#notice"),
    connectionDot: document.querySelector("#connection-dot"),
    connectionLabel: document.querySelector("#connection-label"),
    lastRefresh: document.querySelector("#last-refresh"),
    revision: document.querySelector("#revision"),
    dialog: document.querySelector("#story-dialog"),
    dialogTitle: document.querySelector("#dialog-title"),
    dialogStory: document.querySelector("#dialog-story"),
    dialogStatus: document.querySelector("#dialog-status")
};
const state = { category: "all", snapshot: null, loading: null, refreshing: false, updatePending: null, detailRequest: null };
const publicationDate = new Intl.DateTimeFormat("en", { month: "short", day: "numeric" });
const refreshTime = new Intl.DateTimeFormat("en", { hour: "2-digit", minute: "2-digit", second: "2-digit", hourCycle: "h23" });

function element(tag, className, text) {
    const item = document.createElement(tag);
    if (className) item.className = className;
    if (text !== undefined) item.textContent = text;
    return item;
}

function countStories(count) {
    return `${count} ${count === 1 ? "story" : "stories"}`;
}

function storyLink(rawUrl) {
    if (typeof rawUrl !== "string") return null;
    if (/^\/demo\/[a-z0-9-]+$/.test(rawUrl)) return { href: rawUrl, external: false };
    if (!/^https?:\/\//i.test(rawUrl)) return null;

    try {
        const url = new URL(rawUrl);
        return url.hostname && !url.username && !url.password ? { href: url.href, external: true } : null;
    } catch {
        return null;
    }
}

function createArt(url, variant) {
    const figure = element("figure", "story-media");
    const image = document.createElement("img");
    image.alt = "";
    image.loading = variant === "more" ? "lazy" : "eager";
    if (variant === "lead") image.fetchPriority = "high";
    image.addEventListener("error", () => figure.remove(), { once: true });
    image.src = url;
    figure.append(image);
    return figure;
}

function createMeta(article) {
    const meta = element("div", "story-meta");
    meta.append(element("span", "story-category", categories[article.category] ?? "Other"));
    meta.append(element("span", "", article.source));
    const publishedAt = new Date(article.publishedAt);
    if (article.publishedAt && Number.isFinite(publishedAt.getTime())) {
        const time = element("time", "story-date", publicationDate.format(publishedAt));
        time.dateTime = article.publishedAt;
        meta.append(time);
    }

    return meta;
}

function createTags(tags) {
    const list = element("ul", "story-tags");
    list.setAttribute("aria-label", "Tags");
    for (const tag of tags) list.append(element("li", "", tag));
    return list;
}

function createStory(article, variant, headingTag = "h3") {
    const story = element("article", `story story--${variant}`);
    const imageUrl = demoArt.get(article.url);
    if (imageUrl) story.append(createArt(imageUrl, variant));
    const headline = document.createElement(headingTag);
    const url = storyLink(article.url);
    if (url?.external) {
        const link = element("a", "story-link", article.title);
        link.href = url.href;
        link.target = "_blank";
        link.rel = "noopener noreferrer";
        headline.append(link);
    } else if (url) {
        const button = element("button", "story-link", article.title);
        button.type = "button";
        button.setAttribute("aria-haspopup", "dialog");
        button.addEventListener("click", () => showDemoStory(url.href, article));
        headline.append(button);
    } else {
        headline.textContent = article.title;
    }
    story.append(createMeta(article), headline);

    if ((variant !== "compact" || !imageUrl) && article.summary) {
        story.append(element("p", "story-summary", article.summary));
    }
    if (article.tags?.length) story.append(createTags(article.tags.slice(0, 3)));
    return story;
}

function renderDetail(article) {
    ui.dialogTitle.textContent = article.title;
    const details = [createMeta(article)];
    const imageUrl = demoArt.get(article.url);
    if (imageUrl) {
        const art = createArt(imageUrl, "detail");
        art.append(element("figcaption", "image-caption", "Illustrative image"));
        details.push(art);
    }
    if (article.summary) details.push(element("p", "dialog-summary", article.summary));
    if (article.tags?.length) details.push(createTags(article.tags));
    ui.dialogStory.replaceChildren(...details);
}

async function showDemoStory(url, article) {
    state.detailRequest?.abort();
    const request = new AbortController();
    state.detailRequest = request;
    renderDetail(article);
    ui.dialogStatus.textContent = "Loading story…";
    ui.dialog.showModal();

    try {
        const details = await requestJson(url, { signal: request.signal });
        if (state.detailRequest !== request) return;
        renderDetail(details);
        ui.dialogStatus.textContent = "";
    } catch {
        if (state.detailRequest === request) ui.dialogStatus.textContent = "Showing the summary from the feed.";
    }
}

function filtersAreActive() {
    return state.category !== "all" || ui.source.value !== "all" || ui.search.value.trim() !== "";
}

function resetFilters() {
    clearTimeout(searchTimer);
    ui.search.value = "";
    ui.source.value = "all";
    ui.sort.value = "latest";
    ui.search.focus();
    selectCategory("all");
}

function showEmpty() {
    const filtered = filtersAreActive();
    const empty = element("div", "empty-state");
    empty.append(element("h3", "", filtered ? "No matching stories" : "No stories available"));
    empty.append(element("p", "", filtered ? "Try a different search or clear your filters." : "Refresh the feed or open the demo edition."));
    if (filtered) {
        const clear = element("button", "text-button", "Clear filters");
        clear.type = "button";
        clear.addEventListener("click", resetFilters);
        empty.append(clear);
    }
    ui.stories.replaceChildren(empty);
}

function renderStories(articles) {
    ui.storyCount.textContent = countStories(articles.length);
    if (!articles.length) {
        showEmpty();
        return;
    }

    const feature = element("div", "feature-grid");
    feature.append(createStory(articles[0], "lead"));
    if (articles.length > 1) {
        const secondary = element("div", "secondary-stories");
        for (const story of articles.slice(1, 3)) secondary.append(createStory(story, "compact"));
        feature.append(secondary);
    }
    ui.stories.replaceChildren(feature);
    if (articles.length > 3) {
        const more = element("section", "more-stories");
        const grid = element("div", "more-grid");
        for (const story of articles.slice(3)) grid.append(createStory(story, "more", "h4"));
        more.append(element("h3", "", "More stories"), grid);
        ui.stories.append(more);
    }
}

function renderSources(sources) {
    const selectedSource = ui.source.value;
    const options = [new Option("All sources", "all"), ...sources.map(source => new Option(source.name, source.id))];
    ui.source.replaceChildren(...options);
    ui.source.value = options.some(option => option.value === selectedSource) ? selectedSource : "all";

    const rows = sources.map(source => {
        const item = element("div", "source-item");
        const row = element("div", "source-row");
        const format = element("span", "source-format", source.format.toUpperCase());
        format.dataset.format = source.format.toLowerCase();
        const labels = element("div");
        const status = source.state === "ok" ? countStories(source.count) : "Unavailable";
        labels.append(element("strong", "source-name", source.name), element("span", "source-count", status));
        const dot = element("span", "source-dot");
        dot.dataset.state = source.state;
        dot.setAttribute("aria-hidden", "true");
        row.append(format, labels, dot);
        item.append(row);
        if (source.state === "error" && source.detail) item.append(element("p", "source-detail", source.detail));
        return item;
    });
    ui.sources.replaceChildren(...rows);
}

function render(snapshot) {
    const previousSnapshot = state.snapshot;
    state.snapshot = snapshot;
    renderStories(snapshot.articles);
    if (previousSnapshot?.revision === snapshot.revision && previousSnapshot.updatedAt === snapshot.updatedAt) return;

    renderSources(snapshot.sources);
    ui.sourceNote.hidden = snapshot.sources.length === 0;
    ui.editionNote.hidden = snapshot.mode !== "demo";
    ui.lastRefresh.textContent = refreshTime.format(new Date(snapshot.updatedAt));
    ui.revision.textContent = snapshot.revision;
    for (const button of ui.modeButtons) button.setAttribute("aria-pressed", String(button.dataset.mode === snapshot.mode));
}

async function requestJson(url, options) {
    const response = await fetch(url, options);
    const result = await response.json();
    if (!response.ok) throw new Error(result.error || `HTTP ${response.status}`);
    return result;
}

function showNotice(message = "") {
    ui.notice.textContent = message;
    ui.notice.hidden = !message;
}

async function loadFeed() {
    ui.reset.hidden = !filtersAreActive() && ui.sort.value === "latest";
    state.loading?.abort();
    const request = new AbortController();
    state.loading = request;
    const query = new URLSearchParams({
        q: ui.search.value.trim(), category: state.category, source: ui.source.value, sort: ui.sort.value
    });
    ui.stories.setAttribute("aria-busy", "true");
    try {
        const snapshot = await requestJson(`/api/feed?${query}`, { signal: request.signal });
        if (state.loading !== request) return;
        render(snapshot);
        showNotice();
    } catch {
        if (request.signal.aborted || state.loading !== request) return;
        if (state.snapshot) {
            showNotice("The feed couldn’t be loaded. The last loaded stories are still visible.");
        } else {
            const empty = element("div", "empty-state");
            empty.append(element("h3", "", "Couldn’t load the feed"), element("p", "", "Use Refresh feed to try again."));
            ui.stories.replaceChildren(empty);
        }
    } finally {
        if (state.loading === request) {
            ui.stories.setAttribute("aria-busy", "false");
            state.loading = null;
        }
    }
}

function selectCategory(category) {
    state.category = category;
    ui.storiesTitle.textContent = categories[category];
    for (const button of ui.categoryButtons) button.setAttribute("aria-pressed", String(button.dataset.category === category));
    loadFeed();
}

async function refreshFeed(mode) {
    if (state.refreshing) return;
    state.refreshing = true;
    showNotice();
    ui.refreshLabel.textContent = "Refreshing…";
    for (const button of [ui.refresh, ...ui.modeButtons]) button.setAttribute("aria-disabled", "true");
    try {
        await requestJson(`/api/refresh?${new URLSearchParams({ mode })}`, { method: "POST" });
        await loadFeed();
    } catch (error) {
        showNotice(error.message || "The refresh couldn’t be completed. Try again in a moment.");
    } finally {
        state.refreshing = false;
        ui.refreshLabel.textContent = "Refresh feed";
        for (const button of [ui.refresh, ...ui.modeButtons]) button.removeAttribute("aria-disabled");
        const pending = state.updatePending;
        state.updatePending = null;
        if (pending && pending.revision > (state.snapshot?.revision ?? 0)) loadFeed();
    }
}

function setConnection(status, label) {
    ui.connectionDot.dataset.state = status;
    ui.connectionLabel.textContent = label;
}

function observeFeed() {
    if (!("EventSource" in window)) {
        setConnection("error", "Automatic updates unavailable");
        return;
    }
    const events = new EventSource("/api/events");
    let opened = false;
    events.addEventListener("open", () => {
        setConnection("ok", "Listening for updates");
        if (opened) loadFeed();
        opened = true;
    });
    events.addEventListener("error", () => setConnection("connecting", "Reconnecting…"));
    events.addEventListener("feed", event => {
        const update = JSON.parse(event.data);
        if (update.revision === state.snapshot?.revision && update.mode === state.snapshot.mode) return;
        if (state.refreshing) state.updatePending = update;
        else loadFeed();
    });
}

let searchTimer;
ui.search.addEventListener("input", () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(loadFeed, 180);
});
ui.searchForm.addEventListener("submit", event => {
    event.preventDefault();
    clearTimeout(searchTimer);
    loadFeed();
});
for (const button of ui.categoryButtons) button.addEventListener("click", () => selectCategory(button.dataset.category));
for (const button of ui.modeButtons) {
    button.addEventListener("click", () => {
        if (button.dataset.mode !== state.snapshot?.mode) refreshFeed(button.dataset.mode);
    });
}
ui.source.addEventListener("change", loadFeed);
ui.sort.addEventListener("change", loadFeed);
ui.reset.addEventListener("click", resetFilters);
ui.refresh.addEventListener("click", () => refreshFeed(state.snapshot?.mode ?? "demo"));
ui.dialog.addEventListener("close", () => {
    state.detailRequest?.abort();
    state.detailRequest = null;
});
loadFeed();
observeFeed();
