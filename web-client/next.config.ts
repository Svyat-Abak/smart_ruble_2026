import type { NextConfig } from "next";
const config: NextConfig = {
  async rewrites() {
    const gateway = process.env.GATEWAY_URL || "http://localhost:8080";
    return [
      { source: "/api/v1/:path*", destination: gateway + "/api/v1/:path*" },
    ];
  },
};
export default config;
