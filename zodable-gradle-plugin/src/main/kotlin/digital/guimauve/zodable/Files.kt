package digital.guimauve.zodable

object Files {

    const val ZOD_DESCRIPTION = "Auto-generated zod project from Kotlin using guimauvedigital/zodable"

    fun generatePyProjectToml(
        name: String,
        version: String,
    ): String =
        """
        import sys
        import toml
        import subprocess
        
        project = {
            "name": "$name",
            "version": "$version",
            "description": "Auto-generated Pydantic project from Kotlin using guimauvedigital/zodable",
            "dependencies": [],
        }
        
        with open("requirements.txt", "r") as f:
            deps = [line.strip() for line in f if line.strip() and not line.startswith("#")]
        project["dependencies"] = [dep.replace("==", ">=") for dep in deps]

        pyproject = {
            "project": project,
            "build-system": {
                "requires": ["setuptools", "wheel"],
                "build-backend": "setuptools.build_meta"
            }
        }
        
        with open("pyproject.toml", "w") as f:
            toml.dump(pyproject, f)
        """.trimIndent()

}
