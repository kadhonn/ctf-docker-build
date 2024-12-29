package at.abl.ctf.docker_build

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DockerBuildApplication

fun main(args: Array<String>) {
	runApplication<DockerBuildApplication>(*args)
}
