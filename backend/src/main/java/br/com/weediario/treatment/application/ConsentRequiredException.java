package br.com.weediario.treatment.application;

/** A conta ainda não aceitou (ou revogou) o termo de tratamento de dados de saúde. */
public class ConsentRequiredException extends RuntimeException {

    public ConsentRequiredException() {
        super("Aceite o termo de uso de dados de saúde para usar o acompanhamento de tratamento");
    }
}
