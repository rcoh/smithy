package software.amazon.smithy.codegen.traits;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;

public class StringTraitGeneratorTest {
    @Test
    public void generatesStringTraits() {
        Model model = Model.assembler()
                .addImport(getClass().getResource("test-traits.smithy"))
                .assemble()
                .unwrap();
        TraitGenerator generator = TraitGenerator.create(model, "smithy.example.codegen#myStringTrait");

        System.out.println(generator.getCode());
    }
}