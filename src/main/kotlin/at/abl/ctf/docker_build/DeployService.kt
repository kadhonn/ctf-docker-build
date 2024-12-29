package at.abl.ctf.docker_build

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectory

@Service
class DeployService(
    @Value("\${url.isHttps}") val isHttps: Boolean,
    @Value("\${url.suffix}") val urlSuffix: String,
    val environment: Environment
) {
    @OptIn(ExperimentalPathApi::class)
    fun deploy(
        name: String,
        zipfile: ByteArray
    ): String {
        //TODO validate name
        //TODO validate zip
        val tmpFolder = Files.createTempDirectory("mystaticfiles")
        println(tmpFolder)
        try {
            unzipFile(tmpFolder, zipfile)
            writeDockerFile(tmpFolder)
            buildNewImage(tmpFolder, name)
            val url = "http${if (isHttps) "s" else ""}://" + name + urlSuffix
            runImage(name, url)
            return url
        } catch (e: Exception) {
            throw RuntimeException("error deploying", e)
        } finally {
//            tmpFolder.deleteRecursively()
        }
    }

    private fun runImage(name: String, url: String) {
        println("running image $name")
        val command = mutableListOf("docker", "run", "--rm", "-d")
        command.addAll(getTraefikLabels(name, url))
        command.add(getDockerName(name))
        ProcessBuilder(command)
            .inheritIO()
            .start()
            .waitFor()
        //TODO run in background
        //TODO start cleanup job
    }

    private fun getTraefikLabels(name: String, url: String): Collection<String> {
        val routerName = getDockerName(name)
        if (isHttps) {
            return listOf(
                "-l", "traefik.enable=true",
                "-l", "traefik.http.routers.$routerName.rule=Host(`${url}`)",
                "-l", "traefik.http.routers.$routerName.entrypoints=websecure",
                "-l", "traefik.http.routers.$routerName.tls.certresolver=myresolver"
            )
        } else {
            return listOf(
                "-l", "traefik.enable=true",
                "-l", "traefik.http.routers.$routerName.rule=Host(`${url}`)",
                "-l", "traefik.http.routers.$routerName.entrypoints=web"
            )
        }
    }

    private fun buildNewImage(tmpFolder: Path, name: String) {
        println("building image $name")
        ProcessBuilder("docker", "build", "-t", getDockerName(name), ".")
            .directory(tmpFolder.toFile())
            .inheritIO()
            .start()
            .waitFor()
    }

    private fun getDockerName(name: String) =
        "mystaticsite-$name"


    private fun writeDockerFile(tmpFolder: Path) {
        val dockerfile = this.javaClass.getResourceAsStream("/Dockerfile")!!.readAllBytes()
        Files.write(tmpFolder.resolve("Dockerfile"), dockerfile)
    }

    private fun unzipFile(tmpFolder: Path, zipfile: ByteArray) {
        val dataFolder = tmpFolder.resolve("data")
        dataFolder.createDirectory()
        val zipIn = ZipInputStream(ByteArrayInputStream(zipfile))
        while (true) {
            val nextEntry = zipIn.nextEntry
            if (nextEntry == null) {
                break
            }

            if (nextEntry.isDirectory) {
                throw IllegalArgumentException("folders are not allowed!")
            }

            //TODO sanitize name?
            val file = dataFolder.resolve(nextEntry.name)
            Files.write(file, zipIn.readAllBytes())
        }
    }
}