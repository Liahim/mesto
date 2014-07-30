#include <sys/mman.h>

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
#include <sys/types.h>
#include <fcntl.h>
#include <syslog.h>

#include <sys/wait.h>
#include <unistd.h>
#include <time.h>
#include <signal.h>

void doprocessing(int sock);

static double* glob_var;
static const char* SYSLOG_TAG="Mesto";

int main(int argc, char *argv[]) {
	openlog(SYSLOG_TAG,LOG_PERROR,0);
	syslog(LOG_INFO, "starting up");

	struct sigaction sigchld_action = {
	  .sa_handler = SIG_DFL,
	  .sa_flags = SA_NOCLDWAIT
	};
	sigaction(SIGCHLD, &sigchld_action, NULL);

	glob_var = mmap(NULL, sizeof *glob_var, PROT_READ | PROT_WRITE,
			MAP_SHARED | MAP_ANONYMOUS, -1, 0);
	if (MAP_FAILED == glob_var) {
		syslog(LOG_ERR, "mmap failed");
		exit(1);
	}

	int sockfd, newsockfd, portno;
	socklen_t clilen;
	struct sockaddr_in serv_addr, cli_addr;

	/* First call to socket() function */
	sockfd = socket(AF_INET, SOCK_STREAM, 0);
	if (sockfd < 0) {
		syslog(LOG_ERR, "ERROR opening socket");
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
		syslog(LOG_ERR, "ERROR on binding");
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
			syslog(LOG_ERR, "ERROR on accept");
			exit(1);
		}
		/* Create child process */
		int pid = fork();
		if (pid < 0) {
			syslog(LOG_ERR, "ERROR on fork");
			exit(1);
		}
		if (pid == 0) {
			/* This is the client process */
			close(sockfd);
			doprocessing(newsockfd);
			close(newsockfd);
			exit(0);
		}else{
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
		syslog(LOG_ERR, "ERROR reading from socket");
		exit(1);
	}

	union myunion {
		char b[8];
		double d;
	} lat, lon;

	int offset = 0;

	//udn utf-8 string
	short udnLength = buffer[offset++]<<8;
	udnLength |= buffer[offset++];

	char* udn = malloc(udnLength);
	memcpy(udn, buffer+offset, udnLength);
	offset += udnLength;

	//title utf-8 string
	short titleLength = buffer[offset++]<<8;
	titleLength |= buffer[offset++];

	char* title = malloc(udnLength);
	memcpy(title, buffer+offset, titleLength);
	offset += titleLength;

	lon.b[7] = buffer[offset++];
	lon.b[6] = buffer[offset++];
	lon.b[5] = buffer[offset++];
	lon.b[4] = buffer[offset++];
	lon.b[3] = buffer[offset++];
	lon.b[2] = buffer[offset++];
	lon.b[1] = buffer[offset++];
	lon.b[0] = buffer[offset++];
	*glob_var = lon.d;

	lat.b[7] = buffer[offset++];
	lat.b[6] = buffer[offset++];
	lat.b[5] = buffer[offset++];
	lat.b[4] = buffer[offset++];
	lat.b[3] = buffer[offset++];
	lat.b[2] = buffer[offset++];
	lat.b[1] = buffer[offset++];
	lat.b[0] = buffer[offset++];

	int fd = open ("/opt/share/httpd/coord.json", O_WRONLY | O_CREAT | O_TRUNC, 0644);
	char msg[128] = {0};
	sprintf(msg, "{\"lon\":%.6lf,\"lat\":%.6lf,\"title\":\"%s\"}", lon.d, lat.d, title);
	write(fd, msg, strlen(msg));
	close(fd);

	free(udn);
	free(title);

	time_t t = time(NULL);
	struct tm tm = *localtime(&t);
	printf("%d-%d-%d %d:%d:%d\n", tm.tm_year + 1900, tm.tm_mon + 1, tm.tm_mday,
			tm.tm_hour, tm.tm_min, tm.tm_sec);

	syslog(LOG_INFO, "Here is the message: %f, %f",lat.d, lon.d);

	n = write(sock, "I got your message", 18);
}

