package br.com.weediario.moderation.presentation.rest;

import br.com.weediario.moderation.application.query.ModerationQueries.Counts;
import br.com.weediario.moderation.application.query.ModerationQueries.LogRow;
import br.com.weediario.moderation.application.usecase.ModerateContentUseCase;
import br.com.weediario.moderation.application.usecase.ModerateContentUseCase.Decision;
import br.com.weediario.moderation.application.usecase.ModerationQueueUseCase;
import br.com.weediario.moderation.application.usecase.ModerationQueueUseCase.QueueItem;
import br.com.weediario.moderation.domain.model.ModerationLogEntry;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.AccountAction;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.ModerationReason;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.TargetType;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.Verdict;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.shared.presentation.Viewer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Painel da moderação: fila unificada, decisões e (só admin) exportação do log de moderação. */
@RestController
@RequestMapping("/api/v1/moderation")
public class ModerationController {

    private final ModerationQueueUseCase queue;
    private final ModerateContentUseCase moderate;

    public ModerationController(ModerationQueueUseCase queue, ModerateContentUseCase moderate) {
        this.queue = queue;
        this.moderate = moderate;
    }

    @GetMapping("/queue")
    public PageResult<QueueItem> queue(@AuthenticationPrincipal Jwt principal,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return queue.queue(new PageQuery(page, size), Viewer.isModerator(principal));
    }

    @GetMapping("/queue/counts")
    public Counts counts(@AuthenticationPrincipal Jwt principal) {
        return queue.counts(Viewer.isModerator(principal));
    }

    @PostMapping("/decisions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decide(@AuthenticationPrincipal Jwt principal, @Valid @RequestBody DecisionRequest request) {
        moderate.decide(Viewer.requireId(principal), Viewer.isModerator(principal), new Decision(request.targetType(),
                request.targetId(), request.verdict(), request.account(), request.reason(), request.note()));
    }

    @PostMapping("/users/{username}/reinstate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reinstate(@AuthenticationPrincipal Jwt principal, @PathVariable @Size(max = 30) String username,
                          @Valid @RequestBody(required = false) NoteRequest request) {
        moderate.reinstate(Viewer.requireId(principal), Viewer.isModerator(principal), username,
                request == null ? null : request.note());
    }

    /**
     * Log de moderação em CSV (para ordem judicial ou questionamento). Só administradores. Sem IP e sem quem
     * denunciou: data, conteúdo (tipo e id), conta, moderador, ação, motivo e o hash do conteúdo removido.
     */
    @GetMapping("/log.csv")
    public ResponseEntity<byte[]> log(@AuthenticationPrincipal Jwt principal,
                                      @RequestParam(required = false) @Size(max = 30) String username,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<LogRow> rows = queue.log(Viewer.isAdmin(principal), username,
                from == null ? null : from.atStartOfDay().toInstant(ZoneOffset.UTC),
                to == null ? null : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        StringBuilder csv = new StringBuilder("data_hora_utc,tipo,conteudo_id,conta,moderador,acao,motivo,observacao,sha256_conteudo\n");
        for (LogRow row : rows) {
            csv.append(String.join(",", row.at().toString(), row.targetType(), row.targetId().toString(),
                    cell(row.reportedUsername()), cell(row.moderator()), row.action(), row.reason(), cell(row.note()),
                    cell(row.contentSha256()))).append('\n');
        }
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("log-moderacao.csv").build().toString())
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Aspas em volta, aspas internas dobradas e sem fórmula de planilha (=, +, -, @ no começo). */
    static String cell(String value) {
        if (value == null) {
            return "";
        }
        String safe = value.replace("\r", " ").replace("\n", " ");
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    public record DecisionRequest(@NotNull TargetType targetType, @NotNull UUID targetId, @NotNull Verdict verdict,
                                  AccountAction account, ModerationReason reason,
                                  @Size(max = ModerationLogEntry.NOTE_MAX_LENGTH) String note) {
    }

    public record NoteRequest(@Size(max = ModerationLogEntry.NOTE_MAX_LENGTH) String note) {
    }
}
