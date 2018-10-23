import java.util.ArrayList;
import java.util.Set;
import java.util.List;

public class TxHandler {

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    private UTXOPool L;

    public TxHandler(UTXOPool utxoPool) {
        L = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        double outputSum = 0;
        ArrayList<Transaction.Output> outputs = tx.getOutputs();
        // (4)
        for (Transaction.Output output : outputs) {
            if (output.value < 0) {
                return false;
            }
            outputSum += output.value;
        }

        // (1), (2), (3)
        ArrayList<UTXO> setUTXO = L.getAllUTXO();
        ArrayList<Transaction.Input> inputs = tx.getInputs();
        double inputSum = 0.0;
        int i = 0;
        for (Transaction.Input input : inputs) {
            UTXO u = new UTXO(input.prevTxHash, input.outputIndex);
            if (!setUTXO.contains(u)) {
                return false;
            }
            setUTXO.remove(u);

            byte[] message = tx.getRawDataToSign(i);
            Transaction.Output txOutput = L.getTxOutput(u);
            if (!Crypto.verifySignature(txOutput.address, message, input.signature)) {
                return false;
            }
            inputSum += txOutput.value;
            i++;
        }

        // (5)
        return inputSum >= outputSum;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        List<Transaction> validTxs = new ArrayList<Transaction>();
        for (Transaction tx : possibleTxs) {
            if (!isValidTx(tx)) {
                continue;
            }
            validTxs.add(tx);
            // remove all inputs from ledger
            for (Transaction.Input input : tx.getInputs()) {
                UTXO u = new UTXO(input.prevTxHash, input.outputIndex);
                L.removeUTXO(u);
            }
            // add new UTXOs and corresponding outputs to ledger
            int outputIndex = 0;
            for (Transaction.Output output : tx.getOutputs()) {
                UTXO newUTXO = new UTXO(tx.getHash(), outputIndex);
                L.addUTXO(newUTXO, output);
                outputIndex++;
            }
        }
        Transaction[] mutValidTxs = new Transaction[validTxs.size()];
        mutValidTxs = validTxs.toArray(mutValidTxs);
        return mutValidTxs;
    }

}
