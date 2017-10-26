package main.exception;

public class InsExistenceException extends Exception{

    /**
     *
     */
    private static final long serialVersionUID = 1L;
    int commit_id;
    int file_id;
    public InsExistenceException(int commit_id,int file_id) {
        this.commit_id=commit_id;
        this.file_id=file_id;
    }

    @Override
    public String getMessage() {
        return "The instance of commit="+this.commit_id+" and file_id="+file_id+" don't extst!";
    }
}

