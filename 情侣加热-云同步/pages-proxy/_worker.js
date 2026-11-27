const ORIGIN = "https://hotwinter-cloud.wumisc.workers.dev";

export default {
  async fetch(request) {
    const incoming = new URL(request.url);
    const target = new URL(incoming.pathname + incoming.search, ORIGIN);

    // 透传原始请求，包括 Authorization、Upgrade 和请求体。
    // Pages Function 运行在 Workers runtime 上，WebSocket 101 会原样返回。
    return fetch(new Request(target, request));
  },
};
