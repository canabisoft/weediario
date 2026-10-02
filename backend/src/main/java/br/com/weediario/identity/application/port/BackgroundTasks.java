package br.com.weediario.identity.application.port;

/**
 * Trabalho que roda depois de a resposta sair. Usado quando o tempo de resposta não pode depender do que existe no
 * banco (ex.: o pedido de redefinição de senha não pode demorar mais para contas que têm e-mail).
 */
public interface BackgroundTasks {

    void submit(Runnable task);
}
