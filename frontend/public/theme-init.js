// Aplica o tema salvo ANTES do Angular carregar. Sem isso, a tela pisca em branco por um instante
// ao abrir o app, o que é justamente o que o grower quer evitar dentro da tenda no escuro.
// Arquivo externo (e não script inline) para funcionar com Content-Security-Policy sem 'unsafe-inline'.
(function () {
  var theme = null;
  try {
    theme = localStorage.getItem('wd-theme');
  } catch (e) {
    // Armazenamento bloqueado (modo privado): segue a preferência do sistema.
  }
  if (theme !== 'light' && theme !== 'dark' && theme !== 'night') {
    // Padrão da marca é o escuro; só usa o claro se o sistema pedir explicitamente.
    theme = window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
  }
  document.documentElement.setAttribute('data-theme', theme);
})();
