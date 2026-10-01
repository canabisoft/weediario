package br.com.weediario.treatment.application.port;

import br.com.weediario.treatment.application.usecase.TreatmentReportUseCase.TreatmentReport;

/** Gera o documento do relatório (PDF) a partir dos dados já consolidados. */
public interface ReportRenderer {

    byte[] render(TreatmentReport report);
}
