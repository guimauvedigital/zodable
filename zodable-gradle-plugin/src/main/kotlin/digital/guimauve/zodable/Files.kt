package digital.guimauve.zodable

object Files {

    const val ZOD_DESCRIPTION = "Auto-generated zod project from Kotlin using guimauvedigital/zodable"

    fun generatePyProjectToml(
        name: String,
        version: String,
        pipExec: String,
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
        
        deps = subprocess.run(["$pipExec", "freeze"], capture_output=True, text=True).stdout.splitlines()
        project["dependencies"] = [dep.split("==")[0] for dep in deps if "==" in dep]
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
