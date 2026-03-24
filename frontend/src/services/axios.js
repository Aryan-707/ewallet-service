import axios from "axios";

const instance = axios.create({ baseURL: process.env.REACT_APP_API_URL ? `https://${process.env.REACT_APP_API_URL}/api/v1` : "http://localhost:8080/api/v1" });
instance.defaults.headers.common["Content-Type"] = "application/json";

export default instance;
