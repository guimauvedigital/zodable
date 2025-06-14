package digital.guimauve.example

import digital.guimauve.zodable.ZodableSchema

@ZodableSchema(
    name = "CustomPython",
    schema = """
        from pydantic import BaseModel
        from typing import List

        class CustomPython(BaseModel):
            name: str
            age: int
            is_active: bool
            tags: List[str]
    """,
    filter = "py"
)
interface CustomPython
