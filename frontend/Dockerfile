# syntax=docker/dockerfile:1@sha256:4edf897a3ffa55b89f906fc8cc78afdb3f1834cc9c7083565e611a8a7d5fe99e

FROM node:24-alpine@sha256:ebfe2f90462722a7a4de65e91990e97fe0d401c70e0e762c5b53302f905ec1c1 AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npx ng build --configuration production

# Imagem oficial do nginx que já roda sem root (usuário nginx, porta 8080).
FROM nginxinc/nginx-unprivileged:1.31-alpine@sha256:b5047b9b76845eac124358373c6852377c52e4b05834a47647e955dd91cb8f1e
# Atualiza os pacotes do Alpine para as correções mais recentes: a imagem base é pinada por digest e fica atrás das
# CVEs já corrigidas no Alpine (curl, openssl, expat, pcre2...). O Dependabot volta a avançar o digest quando a base
# for reconstruída; até lá, isto mantém o SO sem HIGH/CRITICAL com correção. Precisa de root; volta para o uid 101.
USER root
RUN apk upgrade --no-cache
USER 101
COPY nginx/nginx.conf /etc/nginx/nginx.conf
COPY nginx/security-headers.conf /etc/nginx/security-headers.conf
COPY --from=build /build/dist/frontend/browser /usr/share/nginx/html
# A imagem base já roda como o usuário nginx (uid 101); declarado aqui para ninguém depender disso sem ver.
USER 101
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD wget -q --spider http://127.0.0.1:8080/ || exit 1
