package lt.dotask.web;

import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Nustato griežtą tekstinių JSON laukų skaitymą, kad klaidingi tipai nebūtų taisomi tyliai. */
@Configuration
public class JsonConfiguration {
    @Bean
    Jackson2ObjectMapperBuilderCustomizer strictTextFields() {
        return builder -> builder.postConfigurer(mapper -> {
            // Skaičius ar loginė reikšmė neturi tyliai tapti užduoties pavadinimu.
            var textRules = mapper.coercionConfigFor(LogicalType.Textual);
            textRules.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
            textRules.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
            textRules.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        });
    }
}
