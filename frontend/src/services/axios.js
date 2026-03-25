import axios from "axios";

const instance = axios.create({
  baseURL: process.env.REACT_APP_API_URL
    ? `https://${process.env.REACT_APP_API_URL}/api/v1`
    : "http://localhost:8080/api/v1",
  timeout: 30000, // 30 seconds to account for Render free-tier cold starts
});
instance.defaults.headers.common["Content-Type"] = "application/json";

// Retry interceptor: auto-retry once on network/timeout errors (handles Render cold-starts)
instance.interceptors.response.use(
  (response) => response,
  async (error) => {
    const config = error.config;
    if (
      !config._retried &&
      (!error.response || error.code === "ECONNABORTED")
    ) {
      config._retried = true;
      return instance(config);
    }
    return Promise.reject(error);
  }
);

export default instance;
