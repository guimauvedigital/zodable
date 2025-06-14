package digital.guimauve.example

import digital.guimauve.zodable.ZodableSchema

@ZodableSchema(
    name = "CustomTypescript",
    schema = """
        import { z } from 'zod';
        
        export const CustomTypescriptSchema = z.object({
            name: z.string(),
            age: z.number().int(),
            isActive: z.boolean(),
            tags: z.array(z.string()),
        });
        export type CustomTypescript = z.infer<typeof CustomTypescriptSchema>;
    """,
    filter = "ts"
)
interface CustomTypescript
