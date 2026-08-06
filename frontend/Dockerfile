# Static HTML/CSS/JS only - no build step/bundler (spec.md Section 4 hard constraint).
# Serves frontend-user/, frontend-business/, frontend-shared/ exactly as they are on disk,
# so http://<host>:8081/frontend-user/index.html and /frontend-business/ops.html both work.

FROM nginx:alpine
COPY . /usr/share/nginx/html
EXPOSE 80
