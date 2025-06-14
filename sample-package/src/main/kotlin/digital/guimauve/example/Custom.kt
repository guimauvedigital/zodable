package digital.guimauve.example

import digital.guimauve.zodable.ZodOverrideSchema
import digital.guimauve.zodable.Zodable

@Zodable
@ZodOverrideSchema(
    content = """
        export const CustomSchema = z.object({
            name: z.string(),
            age: z.number().int(),
            isActive: z.boolean(),
            tags: z.array(z.string()),
        })
        export type Custom = z.infer<typeof CustomSchema>
    """,
    filter = "ts"
)
@ZodOverrideSchema(
    content = """
        from typing import List

        class Custom(BaseModel):
            name: str
            age: int
            is_active: bool
            tags: List[str]
    """,
    filter = "py"
)
interface Custom
