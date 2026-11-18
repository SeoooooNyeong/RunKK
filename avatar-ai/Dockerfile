FROM node:22-bookworm-slim
ENV NODE_ENV=production HOST=0.0.0.0 PORT=3000
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY --chown=node:node *.mjs ./
COPY --chown=node:node public ./public
# 운영 도구(저장된 머리 재압축 등). docker exec로 돌린다
COPY --chown=node:node scripts ./scripts
RUN mkdir -p /app/data && chown node:node /app/data
USER node
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 CMD ["node","--input-type=module","-e","const r=await fetch('http://127.0.0.1:3000/api/health',{signal:AbortSignal.timeout(4000)});if(!r.ok||!(await r.json()).ok)process.exit(1)"]
CMD ["node","server.mjs"]
