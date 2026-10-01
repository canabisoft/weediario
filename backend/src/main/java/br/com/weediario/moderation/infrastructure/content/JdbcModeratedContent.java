package br.com.weediario.moderation.infrastructure.content;

import br.com.weediario.moderation.application.port.ModeratedContent;
import br.com.weediario.moderation.domain.model.ContentRef;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.TargetType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A moderação age sobre tabelas de vários contextos. Cada tipo tem SQL fixo (nunca montado com texto do
 * usuário). Regras de remoção:
 * <ul>
 *   <li>comentários e reviews são apagados;</li>
 *   <li>diários e receitas ficam privados e travados (o dono continua vendo, mas não consegue republicar);</li>
 *   <li>no perfil, bio e foto são apagadas.</li>
 * </ul>
 */
@Component
public class JdbcModeratedContent implements ModeratedContent {

    private record Sql(String find, String hide, String restore, String remove) {
    }

    private static final Map<TargetType, Sql> SQL = new EnumMap<>(TargetType.class);

    static {
        SQL.put(TargetType.COMMENT, new Sql("""
                select c.id, c.author_id, u.username, u.status, d.owner_id as container_owner,
                       (d.visibility = 'PUBLIC' and not c.hidden) as public, d.title, c.body as text, d.id::text as link
                  from diary_comments c
                  join users u on u.id = c.author_id
                  join grow_diaries d on d.id = c.diary_id
                 where c.id = :id""",
                "update diary_comments set hidden = true where id = :id",
                "update diary_comments set hidden = false where id = :id",
                "delete from diary_comments where id = :id"));
        SQL.put(TargetType.DIARY, new Sql("""
                select d.id, d.owner_id as author_id, u.username, u.status, null::uuid as container_owner,
                       d.visibility = 'PUBLIC' as public, d.title,
                       d.title || coalesce(E'\\n' || (select string_agg(n.note, E'\\n' order by n.recorded_on, n.created_at)
                                                       from diary_updates n where n.diary_id = d.id and n.note is not null), '')
                           as text,
                       d.id::text as link
                  from grow_diaries d
                  join users u on u.id = d.owner_id
                 where d.id = :id""",
                "update grow_diaries set moderation_hold = true, visibility = 'PRIVATE' where id = :id",
                """
                update grow_diaries set visibility = case when moderation_hold then 'PUBLIC' else visibility end,
                                        moderation_hold = false
                 where id = :id""",
                "update grow_diaries set moderation_hold = true, visibility = 'PRIVATE' where id = :id"));
        SQL.put(TargetType.REVIEW, new Sql("""
                select r.id, r.author_id, u.username, u.status, null::uuid as container_owner, not r.hidden as public,
                       s.name as title, r.report as text, s.id::text as link
                  from reviews r
                  join users u on u.id = r.author_id
                  join strains s on s.id = r.strain_id
                 where r.id = :id""",
                "update reviews set hidden = true where id = :id",
                "update reviews set hidden = false where id = :id",
                "delete from reviews where id = :id"));
        SQL.put(TargetType.RECIPE, new Sql("""
                select rc.id, rc.owner_id as author_id, u.username, u.status, null::uuid as container_owner,
                       rc.visibility = 'PUBLIC' as public, rc.name as title,
                       rc.name || coalesce(E'\\n' || rc.notes, '') as text, rc.id::text as link
                  from recipes rc
                  join users u on u.id = rc.owner_id
                 where rc.id = :id""",
                "update recipes set moderation_hold = true, visibility = 'PRIVATE' where id = :id",
                """
                update recipes set visibility = case when moderation_hold then 'PUBLIC' else visibility end,
                                   moderation_hold = false
                 where id = :id""",
                "update recipes set moderation_hold = true, visibility = 'PRIVATE' where id = :id"));
        SQL.put(TargetType.RECIPE_COMMENT, new Sql("""
                select c.id, c.author_id, u.username, u.status, rc.owner_id as container_owner,
                       (rc.visibility = 'PUBLIC' and not c.hidden) as public, rc.name as title, c.body as text,
                       rc.id::text as link
                  from recipe_comments c
                  join users u on u.id = c.author_id
                  join recipes rc on rc.id = c.recipe_id
                 where c.id = :id""",
                "update recipe_comments set hidden = true where id = :id",
                "update recipe_comments set hidden = false where id = :id",
                "delete from recipe_comments where id = :id"));
        SQL.put(TargetType.PROFILE, new Sql("""
                select u.id, u.id as author_id, u.username, u.status, null::uuid as container_owner,
                       (u.status = 'ACTIVE' and not u.profile_hidden) as public, u.username as title,
                       coalesce(u.bio, '') as text, u.username as link
                  from users u
                 where u.id = :id""",
                "update users set profile_hidden = true where id = :id",
                "update users set profile_hidden = false where id = :id",
                "update users set bio = null, avatar_image_id = null, profile_hidden = false where id = :id"));
    }

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcModeratedContent(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ContentInfo> find(ContentRef ref) {
        return jdbc.query(SQL.get(ref.type()).find(), params(ref),
                (rs, n) -> new ContentInfo(ref, rs.getObject("author_id", UUID.class), rs.getString("username"),
                        rs.getString("status"), rs.getObject("container_owner", UUID.class), rs.getBoolean("public"),
                        rs.getString("title"), rs.getString("text"), rs.getString("link"))).stream().findFirst();
    }

    @Override
    public void hide(ContentRef ref) {
        jdbc.update(SQL.get(ref.type()).hide(), params(ref));
    }

    @Override
    public void restore(ContentRef ref) {
        jdbc.update(SQL.get(ref.type()).restore(), params(ref));
    }

    @Override
    public void remove(ContentRef ref) {
        jdbc.update(SQL.get(ref.type()).remove(), params(ref));
    }

    private static MapSqlParameterSource params(ContentRef ref) {
        return new MapSqlParameterSource("id", ref.id());
    }
}
