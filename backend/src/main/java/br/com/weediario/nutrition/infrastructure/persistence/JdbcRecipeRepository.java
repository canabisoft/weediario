package br.com.weediario.nutrition.infrastructure.persistence;

import br.com.weediario.nutrition.domain.model.Ingredient;
import br.com.weediario.nutrition.domain.model.IngredientUnit;
import br.com.weediario.nutrition.domain.model.Recipe;
import br.com.weediario.nutrition.domain.model.RecipeApplication;
import br.com.weediario.nutrition.domain.model.RecipeDetails;
import br.com.weediario.nutrition.domain.model.RecipeId;
import br.com.weediario.nutrition.domain.model.RecipeKind;
import br.com.weediario.nutrition.domain.model.RecipeStage;
import br.com.weediario.nutrition.domain.model.RecipeVisibility;
import br.com.weediario.nutrition.domain.repository.RecipeRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcRecipeRepository implements RecipeRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcRecipeRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Recipe save(Recipe recipe) {
        RecipeDetails details = recipe.details();
        UUID id = recipe.id().value();
        jdbc.update("""
                insert into recipes (id, owner_id, name, kind, application, stage, batch_liters, target_ec, target_ph,
                                     notes, visibility, replicated_from, created_at, updated_at)
                values (:id, :ownerId, :name, :kind, :application, :stage, :batchLiters, :targetEc, :targetPh,
                        :notes, :visibility, :replicatedFrom, :createdAt, :updatedAt)
                on conflict (id) do update set
                    name = excluded.name, kind = excluded.kind, application = excluded.application,
                    stage = excluded.stage, batch_liters = excluded.batch_liters, target_ec = excluded.target_ec,
                    target_ph = excluded.target_ph, notes = excluded.notes, visibility = excluded.visibility,
                    updated_at = excluded.updated_at""",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("ownerId", recipe.ownerId())
                        .addValue("name", details.name())
                        .addValue("kind", details.kind().name())
                        .addValue("application", details.application().name())
                        .addValue("stage", details.stage() == null ? null : details.stage().name())
                        .addValue("batchLiters", details.batchLiters())
                        .addValue("targetEc", details.targetEc())
                        .addValue("targetPh", details.targetPh())
                        .addValue("notes", details.notes())
                        .addValue("visibility", recipe.visibility().name())
                        .addValue("replicatedFrom", recipe.replicatedFrom().map(RecipeId::value).orElse(null))
                        .addValue("createdAt", Timestamp.from(recipe.createdAt()))
                        .addValue("updatedAt", Timestamp.from(recipe.updatedAt())));

        jdbc.update("delete from recipe_ingredients where recipe_id = :id", new MapSqlParameterSource("id", id));
        List<Ingredient> ingredients = details.ingredients();
        MapSqlParameterSource[] rows = new MapSqlParameterSource[ingredients.size()];
        for (int position = 0; position < ingredients.size(); position++) {
            Ingredient ingredient = ingredients.get(position);
            rows[position] = new MapSqlParameterSource()
                    .addValue("recipeId", id)
                    .addValue("position", position)
                    .addValue("productId", ingredient.productId())
                    .addValue("name", ingredient.name())
                    .addValue("brand", ingredient.brand())
                    .addValue("quantity", ingredient.quantity())
                    .addValue("unit", ingredient.unit().name());
        }
        jdbc.batchUpdate("""
                insert into recipe_ingredients (recipe_id, position, product_id, name, brand, quantity, unit)
                values (:recipeId, :position, :productId, :name, :brand, :quantity, :unit)""", rows);
        return recipe;
    }

    @Override
    public Optional<Recipe> findById(RecipeId id) {
        return findAllById(List.of(id)).stream().findFirst();
    }

    @Override
    public List<Recipe> findAllById(Collection<RecipeId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource("ids", ids.stream().map(RecipeId::value).toList());
        List<Row> rows = jdbc.query("select * from recipes where id in (:ids)", params, (rs, n) -> row(rs));
        List<Recipe> recipes = new ArrayList<>(rows.size());
        for (Row row : rows) {
            List<Ingredient> ingredients = jdbc.query(
                    "select * from recipe_ingredients where recipe_id = :id order by position",
                    new MapSqlParameterSource("id", row.id()),
                    (rs, n) -> new Ingredient(rs.getObject("product_id", UUID.class), rs.getString("name"),
                            rs.getString("brand"), rs.getBigDecimal("quantity"), IngredientUnit.valueOf(rs.getString("unit"))));
            recipes.add(Recipe.restore(new RecipeId(row.id()), row.ownerId(),
                    new RecipeDetails(row.name(), row.kind(), row.application(), row.stage(), row.batchLiters(),
                            row.targetEc(), row.targetPh(), row.notes(), ingredients),
                    row.visibility(), row.replicatedFrom() == null ? null : new RecipeId(row.replicatedFrom()),
                    row.createdAt().toInstant(), row.updatedAt().toInstant()));
        }
        return recipes;
    }

    @Override
    public void delete(RecipeId id) {
        jdbc.update("delete from recipes where id = :id", new MapSqlParameterSource("id", id.value()));
    }

    @Override
    public long countByOwner(UUID ownerId) {
        Long count = jdbc.queryForObject("select count(*) from recipes where owner_id = :ownerId",
                new MapSqlParameterSource("ownerId", ownerId), Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public List<RecipeId> usedInDiary(UUID diaryId) {
        return jdbc.query("select recipe_id from diary_recipes where diary_id = :diaryId order by recipe_id",
                new MapSqlParameterSource("diaryId", diaryId), (rs, n) -> new RecipeId(rs.getObject(1, UUID.class)));
    }

    @Override
    public void replaceDiaryRecipes(UUID diaryId, List<RecipeId> recipeIds) {
        MapSqlParameterSource params = new MapSqlParameterSource("diaryId", diaryId);
        jdbc.update("delete from diary_recipes where diary_id = :diaryId", params);
        for (RecipeId recipeId : recipeIds) {
            jdbc.update("insert into diary_recipes (diary_id, recipe_id) values (:diaryId, :recipeId)",
                    new MapSqlParameterSource("diaryId", diaryId).addValue("recipeId", recipeId.value()));
        }
    }

    private static Row row(ResultSet rs) throws SQLException {
        String stage = rs.getString("stage");
        return new Row(rs.getObject("id", UUID.class), rs.getObject("owner_id", UUID.class), rs.getString("name"),
                RecipeKind.valueOf(rs.getString("kind")), RecipeApplication.valueOf(rs.getString("application")),
                stage == null ? null : RecipeStage.valueOf(stage), rs.getBigDecimal("batch_liters"),
                rs.getBigDecimal("target_ec"), rs.getBigDecimal("target_ph"), rs.getString("notes"),
                RecipeVisibility.valueOf(rs.getString("visibility")), rs.getObject("replicated_from", UUID.class),
                rs.getTimestamp("created_at"), rs.getTimestamp("updated_at"));
    }

    private record Row(UUID id, UUID ownerId, String name, RecipeKind kind, RecipeApplication application,
                       RecipeStage stage, BigDecimal batchLiters, BigDecimal targetEc,
                       BigDecimal targetPh, String notes, RecipeVisibility visibility, UUID replicatedFrom,
                       Timestamp createdAt, Timestamp updatedAt) {
    }
}
