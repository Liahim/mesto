#include <strings.h>
#include <stdlib.h>
#include <stdio.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <unistd.h>
#include <stdio.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <fcntl.h>

#include <sys/wait.h>
#include <unistd.h>
#include <time.h>

void doprocessing(int sock);

static double* glob_var;

int main(int argc, char *argv[]) {
	struct sigaction sigchld_action = {
	  .sa_handler = SIG_DFL,
	  .sa_flags = SA_NOCLDWAIT
	};
	sigaction(SIGCHLD, &sigchld_action, NULL);

	glob_var = mmap(NULL, sizeof *glob_var, PROT_READ | PROT_WRITE,
	MAP_SHARED | MAP_ANONYMOUS, -1, 0);
	if (MAP_FAILED == glob_var) {
		perror("mmap failed");
		exit(1);
	}

	int sockfd, newsockfd, portno;
	socklen_t clilen;
	struct sockaddr_in serv_addr, cli_addr;

	/* First call to socket() function */
	sockfd = socket(AF_INET, SOCK_STREAM, 0);
	if (sockfd < 0) {
		perror("ERROR opening socket");
		exit(1);
	}
	/* Initialize socket structure */
	bzero((char *) &serv_addr, sizeof(serv_addr));
	portno = 5001;
	serv_addr.sin_family = AF_INET;
	serv_addr.sin_addr.s_addr = INADDR_ANY;
	serv_addr.sin_port = htons(portno);

	/* Now bind the host address using bind() call.*/
	if (bind(sockfd, (struct sockaddr *) &serv_addr, sizeof(serv_addr)) < 0) {
		perror("ERROR on binding");
		exit(1);
	}
	/* Now start listening for the clients, here
	 * process will go in sleep mode and will wait
	 * for the incoming connection
	 */
	listen(sockfd, 5);
	clilen = sizeof(cli_addr);
	while (1) {
		newsockfd = accept(sockfd, (struct sockaddr *) &cli_addr, &clilen);
		if (newsockfd < 0) {
			perror("ERROR on accept");
			exit(1);
		}
		/* Create child process */
		int pid = fork();
		if (pid < 0) {
			perror("ERROR on fork");
			exit(1);
		}
		if (pid == 0) {
			/* This is the client process */
			close(sockfd);
			doprocessing(newsockfd);
			exit(0);
		} else {
			close(newsockfd);
		}

	} /* end of while */

	munmap(glob_var, sizeof *glob_var);
}

void doprocessing(int sock) {
	int n;
	char buffer[256];

	bzero(buffer, 256);

	n = read(sock, buffer, 255);
	if (n < 16) {
		perror("ERROR reading from socket");
		exit(1);
	}

	union myunion {
		char b[8];
		double d;
	} lat, lon;

	lon.b[0] = buffer[7];
	lon.b[1] = buffer[6];
	lon.b[2] = buffer[5];
	lon.b[3] = buffer[4];
	lon.b[4] = buffer[3];
	lon.b[5] = buffer[2];
	lon.b[6] = buffer[1];
	lon.b[7] = buffer[0];
	*glob_var = lon.d;

	lat.b[0] = buffer[15];
	lat.b[1] = buffer[14];
	lat.b[2] = buffer[13];
	lat.b[3] = buffer[12];
	lat.b[4] = buffer[11];
	lat.b[5] = buffer[10];
	lat.b[6] = buffer[9];
	lat.b[7] = buffer[8];

	int fd = open ("/opt/share/httpd/coord.json", O_WRONLY | O_CREAT, 0644);
	char msg[128] = {0};
	sprintf(msg, "{\"lon\":%.6lf,\"lat\":%.6lf}", lon.d, lat.d);
	write(fd, msg, strlen(msg));
	close(fd);

	time_t t = time(NULL);
	struct tm tm = *localtime(&t);
	printf("%d-%d-%d %d:%d:%d\n", tm.tm_year + 1900, tm.tm_mon + 1, tm.tm_mday,
			tm.tm_hour, tm.tm_min, tm.tm_sec);

	printf("Here is the message: %f, %f\n", lat.d, lon.d);
	n = write(sock, "I got your message", 18);
	if (n < 0) {
		perror("ERROR writing to socket");
		exit(1);
	}
}

